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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** WhatsApp plain-text sending, against a fake of the WhatsApp Cloud API (nothing leaves the machine). */
@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=test-admin",
        "APP_ADMIN_PASSWORD=integration-test-password-17",
        "spring.datasource.url=jdbc:h2:mem:whatsapp-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.notifications.whatsapp.enabled=true",
        "app.notifications.whatsapp.phone-number-id=1234567890",
        "app.notifications.whatsapp.token=SECRET-TEST-TOKEN",
        "app.notifications.whatsapp.api-version=v99.0"
})
@AutoConfigureMockMvc
class WhatsAppTextGatewayTest {
    record Call(String path, String authorization, String contentType, JsonNode body) { }

    static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    static final AtomicInteger NEXT_STATUS = new AtomicInteger(200);
    static volatile String NEXT_BODY = "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.X\"}]}";
    static final HttpServer FAKE = startFake();

    static HttpServer startFake() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var mapper = new ObjectMapper();
            server.createContext("/", exchange -> {
                var raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                CALLS.add(new Call(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("Content-Type"), mapper.readTree(raw)));
                var response = NEXT_BODY.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(NEXT_STATUS.get(), response.length);
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
    @Autowired SmsGateway gateway;
    @Autowired OrderService orders;

    @BeforeEach
    void reset() { CALLS.clear(); NEXT_STATUS.set(200); NEXT_BODY = "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.X\"}]}"; }

    @Test
    void numbersAreNormalisedToDigitsWithTheCountryCode() {
        assertThat(WhatsAppTextGateway.normalize("84 123 4567")).isEqualTo("258841234567");
        assertThat(WhatsAppTextGateway.normalize("084 123 4567")).isEqualTo("258841234567");
        assertThat(WhatsAppTextGateway.normalize("0258 82 325 5120")).isEqualTo("258823255120");
        assertThat(WhatsAppTextGateway.normalize("+258 84 123 4567")).isEqualTo("258841234567");
        assertThat(WhatsAppTextGateway.normalize("+351 912 345 678")).isEqualTo("351912345678");
        assertThatThrownBy(() -> WhatsAppTextGateway.normalize("123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WhatsAppTextGateway.normalize(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendsAPlainTextMessageInTheFormatTheCloudApiExpects() {
        assertThat(gateway).isInstanceOf(WhatsAppTextGateway.class);
        gateway.send("+258 84 123 4567", "Olá! Os seus bilhetes: https://exemplo.test/ticket.html?t=ABC");

        assertThat(CALLS).hasSize(1);
        var call = CALLS.get(0);
        assertThat(call.path()).isEqualTo("/v99.0/1234567890/messages");
        assertThat(call.authorization()).isEqualTo("Bearer SECRET-TEST-TOKEN");
        assertThat(call.contentType()).startsWith("application/json");
        assertThat(call.body().get("messaging_product").asText()).isEqualTo("whatsapp");
        assertThat(call.body().get("to").asText()).isEqualTo("258841234567");
        assertThat(call.body().get("type").asText()).isEqualTo("text"); // plain text: never a template
        assertThat(call.body().has("template")).isFalse();
        assertThat(call.body().get("text").get("body").asText()).startsWith("Olá! Os seus bilhetes");
        assertThat(call.body().get("text").get("preview_url").asBoolean()).isTrue();
    }

    @Test
    void refusalsFromWhatsAppSurfaceAsFailuresThatNameTheCauseButNeverTheToken() {
        NEXT_STATUS.set(400);
        NEXT_BODY = "{\"error\":{\"message\":\"Re-engagement message\",\"type\":\"OAuthException\",\"code\":131047}}";
        assertThatThrownBy(() -> gateway.send("84 123 4567", "texto secreto da mensagem"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("131047").hasMessageContaining("24 hours")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("SECRET-TEST-TOKEN").doesNotContain("texto secreto"));

        NEXT_BODY = "{\"error\":{\"message\":\"Invalid OAuth access token\",\"code\":190}}";
        assertThatThrownBy(() -> gateway.send("84 123 4567", "x")).hasMessageContaining("token expired or is invalid");

        NEXT_STATUS.set(502); NEXT_BODY = "<html>bad gateway</html>";
        assertThatThrownBy(() -> gateway.send("84 123 4567", "x")).hasMessageContaining("HTTP 502");
    }

    @Test
    void confirmingAReservationMessagesTheCustomerOnWhatsApp() throws Exception {
        var payload = Map.of("fullName", "Rita Zap", "phone", "84 777 1111", "requestedDate", LocalDate.now().plusDays(20).toString(),
                "requestedTime", "19:00", "partySize", 2, "venue", "RESTAURANT");
        var created = mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn();
        var reference = json.readTree(created.getResponse().getContentAsString()).get("reference").asText();
        mvc.perform(patch("/api/admin/reservations/{r}/status", reference).with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());

        await().untilAsserted(() -> assertThat(CALLS).anyMatch(c -> c.body().get("to").asText().equals("258847771111")
                && c.body().get("text").get("body").asText().contains(reference)));
    }

    @Test
    void payingAnOrderSendsTheTicketLinkAndAFailedSendNeverUndoesThePayment() throws Exception {
        var event = json.readTree(mvc.perform(post("/api/admin/events").with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("slug", "zap-night", "title", "Zap Night",
                        "location", "South Beach", "startsAt", "2030-01-01T17:00:00Z", "status", "PUBLISHED"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var type = json.readTree(mvc.perform(post("/api/admin/events/" + event.get("id").asLong() + "/ticket-types")
                .with(httpBasic("test-admin", "integration-test-password-17")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Normal", "priceMinor", 50000, "capacity", 20, "maxPerOrder", 5))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // WhatsApp refuses (customer outside the 24-hour window): the payment is still recorded and the ticket page works.
        NEXT_STATUS.set(400);
        NEXT_BODY = "{\"error\":{\"message\":\"Re-engagement message\",\"code\":131047}}";
        var refused = orders.place(new OrderRequest("zap-night", "Cliente Zap", "+258 84 555 0001", null, List.of(new OrderRequest.Item(type.get("id").asLong(), 1)))).order().getReference();
        var paid = json.readTree(mvc.perform(post("/api/admin/orders/" + refused + "/mark-paid").with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        await().untilAsserted(() -> assertThat(CALLS).anyMatch(c -> c.body().get("to").asText().equals("258845550001")));

        // Delivered: the message carries the customer's private ticket link.
        CALLS.clear(); NEXT_STATUS.set(200);
        var ok = orders.place(new OrderRequest("zap-night", "Cliente Zap 2", "+258 84 555 0002", null, List.of(new OrderRequest.Item(type.get("id").asLong(), 1)))).order().getReference();
        var url = json.readTree(mvc.perform(post("/api/admin/orders/" + ok + "/mark-paid").with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("ticketsUrl").asText();
        var token = url.substring(url.indexOf("t=") + 2);
        await().untilAsserted(() -> assertThat(CALLS).anyMatch(c -> c.body().get("to").asText().equals("258845550002")
                && c.body().get("text").get("body").asText().contains("ticket.html?t=" + token)));
    }
}
