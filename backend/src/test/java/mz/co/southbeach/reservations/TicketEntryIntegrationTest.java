package mz.co.southbeach.reservations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import mz.co.southbeach.tickets.api.dto.OrderRequest;
import mz.co.southbeach.tickets.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=test-admin",
        "APP_ADMIN_PASSWORD=integration-test-password-17",
        "spring.datasource.url=jdbc:h2:mem:entry-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.tickets.expiry-check-ms=3600000"
})
@AutoConfigureMockMvc
class TicketEntryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderService orders;

    private ResultActions admin(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Returns [eventId, typeId]. */
    private long[] event(String slug) throws Exception {
        var event = body(admin(post("/api/admin/events"), Map.of("slug", slug, "title", "Evento " + slug, "location", "South Beach",
                "startsAt", "2030-01-01T17:00:00Z", "status", "PUBLISHED")).andExpect(status().isCreated()));
        var type = body(admin(post("/api/admin/events/" + event.get("id").asLong() + "/ticket-types"), Map.of(
                "name", "VIP", "priceMinor", 100000, "capacity", 50, "maxPerOrder", 10)).andExpect(status().isCreated()));
        return new long[]{event.get("id").asLong(), type.get("id").asLong()};
    }

    private String paidOrder(String slug, long typeId, int quantity) throws Exception {
        var placed = orders.place(new OrderRequest(slug, "Cliente QR", "+258 84 000 2222", null, List.of(new OrderRequest.Item(typeId, quantity))));
        return placed.order().getReference();
    }

    private JsonNode markPaid(String reference) throws Exception {
        return body(admin(post("/api/admin/orders/" + reference + "/mark-paid"), Map.of()).andExpect(status().isOk()));
    }

    private JsonNode passes(String ticketsUrl) throws Exception {
        var token = ticketsUrl.substring(ticketsUrl.indexOf("t=") + 2);
        return body(mvc.perform(get("/api/tickets/" + token)).andExpect(status().isOk()));
    }

    private JsonNode checkIn(long eventId, String code) throws Exception {
        return body(admin(post("/api/admin/check-in"), Map.of("eventId", eventId, "code", code)).andExpect(status().isOk()));
    }

    private JsonNode undo(long eventId, String code) throws Exception {
        return body(admin(post("/api/admin/check-in/undo"), Map.of("eventId", eventId, "code", code)).andExpect(status().isOk()));
    }

    @Test
    void payingIssuesOneUniqueTicketPerSeatOnlyOnce() throws Exception {
        var ids = event("issue-night");
        var reference = paidOrder("issue-night", ids[1], 3);
        var paid = markPaid(reference);
        markPaid(reference); // idempotent
        assertThat(paid.get("status").asText()).isEqualTo("PAID");

        var pass = passes(paid.get("ticketsUrl").asText());
        assertThat(pass.get("tickets")).hasSize(3);
        var codes = new HashSet<String>();
        pass.get("tickets").forEach(t -> codes.add(t.get("code").asText()));
        assertThat(codes).hasSize(3).allMatch(c -> c.length() == 26);
        assertThat(pass.get("tickets").get(0).get("ticketType").asText()).isEqualTo("VIP");
    }

    @Test
    void ticketLinkIsPrivateAndUnpaidOrdersHaveNoTickets() throws Exception {
        var ids = event("private-night");
        var pending = paidOrder("private-night", ids[1], 1);
        mvc.perform(get("/api/tickets/not-a-real-token")).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/orders/" + pending + "/mark-paid")).andExpect(status().isUnauthorized());
        // an unpaid order exposes no ticketsUrl to staff
        var page = body(mvc.perform(get("/api/admin/orders?status=PENDING").with(httpBasic("test-admin", "integration-test-password-17"))));
        for (var order : page.get("content")) {
            if (order.get("reference").asText().equals(pending)) assertThat(order.has("ticketsUrl")).isFalse();
        }
    }

    @Test
    void qrImageEncodesTheTicketCode() throws Exception {
        var ids = event("qr-night");
        var paid = markPaid(paidOrder("qr-night", ids[1], 1));
        var code = passes(paid.get("ticketsUrl").asText()).get("tickets").get(0).get("code").asText();

        var png = mvc.perform(get("/api/tickets/qr/" + code)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        var image = ImageIO.read(new ByteArrayInputStream(png));
        var pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        var decoded = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)))).getText();
        assertThat(decoded).isEqualTo(code);
        mvc.perform(get("/api/tickets/qr/NOSUCHCODE")).andExpect(status().isNotFound());
    }

    @Test
    void gateAdmitsOnceAndExplainsRefusals() throws Exception {
        var ids = event("gate-night");
        var other = event("other-gate-night");
        var code = passes(markPaid(paidOrder("gate-night", ids[1], 1)).get("ticketsUrl").asText()).get("tickets").get(0).get("code").asText();

        mvc.perform(post("/api/admin/check-in").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + ids[0] + ",\"code\":\"" + code + "\"}")).andExpect(status().isUnauthorized());
        assertThat(checkIn(other[0], code).get("outcome").asText()).isEqualTo("WRONG_EVENT");
        assertThat(checkIn(ids[0], "  " + code.toLowerCase() + " ").get("outcome").asText()).isEqualTo("ADMITTED");
        var second = checkIn(ids[0], code);
        assertThat(second.get("outcome").asText()).isEqualTo("ALREADY_USED");
        assertThat(second.get("usedAt").asText()).isNotEmpty();
        assertThat(checkIn(ids[0], "ZZZZZZZZZZZZZZZZZZZZZZZZZZ").get("outcome").asText()).isEqualTo("NOT_FOUND");

        admin(get("/api/admin/events/" + ids[0] + "/entry-stats"), "")
                .andExpect(status().isOk()).andExpect(jsonPath("$.issued", is(1))).andExpect(jsonPath("$.admitted", is(1)))
                .andExpect(jsonPath("$.byType[0].name", is("VIP")));
    }

    @Test
    void staffCanTakeBackAMistakenEntryAndSeeTheLatestAdmissions() throws Exception {
        var ids = event("undo-night");
        var other = event("undo-other-night");
        var code = passes(markPaid(paidOrder("undo-night", ids[1], 1)).get("ticketsUrl").asText()).get("tickets").get(0).get("code").asText();

        assertThat(undo(ids[0], code).get("outcome").asText()).isEqualTo("NOT_USED");
        assertThat(checkIn(ids[0], code).get("outcome").asText()).isEqualTo("ADMITTED");
        admin(get("/api/admin/events/" + ids[0] + "/entry-recent"), "").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code", is(code))).andExpect(jsonPath("$[0].ticketType", is("VIP")));
        assertThat(undo(other[0], code).get("outcome").asText()).isEqualTo("NOT_FOUND");
        assertThat(undo(ids[0], code).get("outcome").asText()).isEqualTo("UNDONE");
        assertThat(checkIn(ids[0], code).get("outcome").asText()).isEqualTo("ADMITTED");
        admin(get("/api/admin/events/" + ids[0] + "/entry-recent"), "").andExpect(status().isOk()).andExpect(jsonPath("$.length()", is(1)));
        mvc.perform(post("/api/admin/check-in/undo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + ids[0] + ",\"code\":\"" + code + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void twoScannersReadingTheSameCodeAdmitOnlyOnePerson() throws Exception {
        var ids = event("scan-race-night");
        var code = passes(markPaid(paidOrder("scan-race-night", ids[1], 1)).get("ticketsUrl").asText()).get("tickets").get(0).get("code").asText();

        var pool = Executors.newFixedThreadPool(10);
        List<Callable<String>> scans = new ArrayList<>();
        for (int i = 0; i < 20; i++) scans.add(() -> checkIn(ids[0], code).get("outcome").asText());
        var outcomes = new ArrayList<String>();
        for (var future : pool.invokeAll(scans)) outcomes.add(future.get());
        pool.shutdown();

        assertThat(outcomes.stream().filter("ADMITTED"::equals)).hasSize(1);
        assertThat(outcomes.stream().filter("ALREADY_USED"::equals)).hasSize(19);
    }
}
