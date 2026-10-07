package mz.co.southbeach.reservations.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import mz.co.southbeach.tickets.api.dto.OrderRequest;
import mz.co.southbeach.tickets.service.OrderService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Customers writing to the business number: the site answers with their tickets or reservation, to the right person only. */
@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=test-admin",
        "APP_ADMIN_PASSWORD=integration-test-password-17",
        "spring.datasource.url=jdbc:h2:mem:whatsapp-webhook-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.notifications.whatsapp.enabled=true",
        "app.notifications.whatsapp.phone-number-id=555000111",
        "app.notifications.whatsapp.token=SECRET-TEST-TOKEN",
        "app.notifications.whatsapp.verify-token=VERIFY-ME",
        "app.notifications.whatsapp.app-secret=APP-SECRET-FOR-TESTS",
        "app.notifications.whatsapp.business-number=+258 86 570 8062",
        "app.site-url=https://site.test"
})
@AutoConfigureMockMvc
class WhatsAppWebhookTest {
    record Sent(String to, String text) { }

    static final List<Sent> SENT = new CopyOnWriteArrayList<>();
    static final HttpServer FAKE = startFake();

    static HttpServer startFake() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var mapper = new ObjectMapper();
            server.createContext("/", exchange -> {
                var rawBytes = exchange.getRequestBody().readAllBytes();
                byte[] response;
                if (exchange.getRequestURI().getPath().endsWith("/media")) {
                    response = "{\"id\":\"MEDIA-1\"}".getBytes(StandardCharsets.UTF_8);
                } else {
                    var body = mapper.readTree(rawBytes);
                    if ("document".equals(body.path("type").asText())) SENT.add(new Sent(body.get("to").asText(), "DOCUMENT:" + body.get("document").get("filename").asText()));
                    else SENT.add(new Sent(body.get("to").asText(), body.get("text").get("body").asText()));
                    response = "{\"messages\":[{\"id\":\"wamid.X\"}]}".getBytes(StandardCharsets.UTF_8);
                }
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    @DynamicPropertySource
    static void fakeApi(DynamicPropertyRegistry registry) {
        registry.add("app.notifications.whatsapp.base-url", () -> "http://127.0.0.1:" + FAKE.getAddress().getPort());
    }

    @AfterAll
    static void stop() { FAKE.stop(0); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderService orders;
    static int counter = 0;

    @BeforeEach
    void reset() { SENT.clear(); }

    private static String sign(String body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("APP-SECRET-FOR-TESTS".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String incoming(String id, String from, String text) throws Exception {
        return json.writeValueAsString(Map.of("object", "whatsapp_business_account", "entry", List.of(Map.of("changes", List.of(Map.of("value",
                Map.of("messages", List.of(Map.of("id", id, "from", from, "type", "text", "text", Map.of("body", text))))))))));
    }

    private void write(String id, String from, String text) throws Exception {
        var body = incoming(id, from, text);
        mvc.perform(post("/api/whatsapp/webhook").header("X-Hub-Signature-256", sign(body)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private Sent reply(String to) {
        // The answer to the customer's message, not the automatic "payment received" notice that may have gone out before it.
        await().untilAsserted(() -> assertThat(SENT).anyMatch(s -> s.to().equals(to) && !s.text().startsWith("South Beach: pagamento recebido") && !s.text().startsWith("DOCUMENT:")));
        return SENT.stream().filter(s -> s.to().equals(to) && !s.text().startsWith("South Beach: pagamento recebido") && !s.text().startsWith("DOCUMENT:")).findFirst().orElseThrow();
    }

    private long ticketType() throws Exception {
        var event = json.readTree(mvc.perform(post("/api/admin/events").with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("slug", "wa-night-" + (++counter), "title", "WA Night",
                        "location", "South Beach", "startsAt", "2030-01-01T17:00:00Z", "status", "PUBLISHED"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var type = json.readTree(mvc.perform(post("/api/admin/events/" + event.get("id").asLong() + "/ticket-types")
                .with(httpBasic("test-admin", "integration-test-password-17")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Normal", "priceMinor", 50000, "capacity", 20, "maxPerOrder", 5))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        lastSlug = "wa-night-" + counter;
        return type.get("id").asLong();
    }
    static String lastSlug;

    private String order(String phone, boolean paid) throws Exception {
        var type = ticketType();
        var ref = orders.place(new OrderRequest(lastSlug, "Maria Souza", phone, null, List.of(new OrderRequest.Item(type, 1)))).order().getReference();
        if (!paid) return ref;
        var url = json.readTree(mvc.perform(post("/api/admin/orders/" + ref + "/mark-paid").with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("ticketsUrl").asText();
        return ref + "|" + url.substring(url.indexOf("t=") + 2);
    }

    @Test
    void metVerifiesTheAddressWithTheSharedTokenOnly() throws Exception {
        mvc.perform(get("/api/whatsapp/webhook").param("hub.mode", "subscribe").param("hub.verify_token", "VERIFY-ME").param("hub.challenge", "12345"))
                .andExpect(status().isOk()).andExpect(content().string("12345"));
        mvc.perform(get("/api/whatsapp/webhook").param("hub.mode", "subscribe").param("hub.verify_token", "wrong").param("hub.challenge", "12345")).andExpect(status().isForbidden());
        mvc.perform(get("/api/whatsapp/webhook")).andExpect(status().isForbidden());
        mvc.perform(get("/api/whatsapp/info")).andExpect(status().isOk()).andExpect(jsonPath("$.number").value("258865708062"));
    }

    @Test
    void callsWithoutMetasSignatureAreIgnored() throws Exception {
        var body = incoming("wamid.unsigned", "258849990001", "Olá");
        mvc.perform(post("/api/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/whatsapp/webhook").header("X-Hub-Signature-256", "sha256=deadbeef").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        Thread.sleep(400);
        assertThat(SENT).isEmpty();
    }

    @Test
    void thePaidCustomerWhoWritesGetsTheTicketLinkWithOrWithoutTheReference() throws Exception {
        var paid = order("+258 84 910 0001", true).split("\\|");
        write("wamid.a1", "258849100001", "Quero os bilhetes da encomenda " + paid[0].toLowerCase());
        var answer = reply("258849100001").text();
        assertThat(answer).contains("https://site.test/ticket.html?t=" + paid[1]).contains(paid[0]).contains("Maria");
        await().untilAsserted(() -> assertThat(SENT).anyMatch(s -> s.to().equals("258849100001") && s.text().equals("DOCUMENT:Bilhetes-" + paid[0] + ".pdf")));

        SENT.clear();
        Thread.sleep(8200); // the automatic-reply gap
        write("wamid.a2", "258849100001", "oi");
        assertThat(reply("258849100001").text()).contains("ticket.html?t=" + paid[1]);
    }

    @Test
    void anotherNumberNeverReceivesSomeoneElsesTickets() throws Exception {
        var paid = order("+258 84 910 0002", true).split("\\|");
        write("wamid.b1", "258849999999", "Encomenda " + paid[0]);
        var answer = reply("258849999999").text();
        assertThat(answer).contains("Não encontrámos").doesNotContain(paid[1]).doesNotContain("ticket.html");
        Thread.sleep(500);
        assertThat(SENT).noneMatch(s -> s.to().equals("258849999999") && s.text().startsWith("DOCUMENT:")); // and no file either
        write("wamid.b2", "258849999998", "TK-AAAAAAAAAAAA"); // an unknown reference gets the same answer
        await().untilAsserted(() -> assertThat(SENT).anyMatch(s -> s.to().equals("258849999998") && s.text().contains("Não encontrámos")));
    }

    @Test
    void anOrderStillWaitingForPaymentIsAcknowledgedAndPaymentLaterSendsTheTickets() throws Exception {
        var ref = order("+258 84 910 0003", false);
        write("wamid.c1", "258849100003", "Quero receber os bilhetes da encomenda " + ref);
        assertThat(reply("258849100003").text()).contains("Recebemos o seu pedido").contains(ref).doesNotContain("ticket.html");

        SENT.clear();
        var url = json.readTree(mvc.perform(post("/api/admin/orders/" + ref + "/mark-paid").with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("ticketsUrl").asText();
        await().untilAsserted(() -> assertThat(SENT).anyMatch(s -> s.to().equals("258849100003") && s.text().contains(url.substring(url.indexOf("ticket.html")))));
    }

    @Test
    void reservationsAreAnsweredWithTheirStatus() throws Exception {
        var created = json.readTree(mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "fullName", "Rita Zap", "phone", "84 910 0004", "requestedDate", LocalDate.now().plusDays(20).toString(),
                "requestedTime", "19:00", "partySize", 4, "venue", "RESTAURANT")))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var ref = created.get("reference").asText();
        write("wamid.d1", "258849100004", "Reserva " + ref);
        assertThat(reply("258849100004").text()).contains("Recebemos o seu pedido de reserva").contains(ref);

        mvc.perform(patch("/api/admin/reservations/{r}/status", ref).with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
        SENT.clear();
        Thread.sleep(8200);
        write("wamid.d2", "258849100004", ref);
        await().untilAsserted(() -> assertThat(SENT).anyMatch(s -> s.to().equals("258849100004") && s.text().contains("confirmada")));
    }

    @Test
    void strangersGetAShortHelpAndRepeatedDeliveriesAnswerOnce() throws Exception {
        write("wamid.e1", "258849100005", "Bom dia");
        assertThat(reply("258849100005").text()).contains("assistente automático").contains("TK-");
        write("wamid.e1", "258849100005", "Bom dia"); // Meta delivered it twice
        Thread.sleep(600);
        assertThat(SENT.stream().filter(s -> s.to().equals("258849100005"))).hasSize(1);
    }
}
