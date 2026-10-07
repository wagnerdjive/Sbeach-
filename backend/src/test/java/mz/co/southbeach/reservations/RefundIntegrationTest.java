package mz.co.southbeach.reservations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;

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
        "spring.datasource.url=jdbc:h2:mem:refund-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.tickets.expiry-check-ms=3600000"
})
@AutoConfigureMockMvc
class RefundIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderService orders;

    private ResultActions admin(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : json.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** [eventId, typeId] for a 10-seat event. */
    private long[] event(String slug) throws Exception {
        var event = body(admin(post("/api/admin/events"), Map.of("slug", slug, "title", "Evento " + slug, "location", "X",
                "startsAt", "2030-01-01T17:00:00Z", "status", "PUBLISHED")).andExpect(status().isCreated()));
        var type = body(admin(post("/api/admin/events/" + event.get("id").asLong() + "/ticket-types"), Map.of(
                "name", "Normal", "priceMinor", 100000, "capacity", 10, "maxPerOrder", 10)).andExpect(status().isCreated()));
        return new long[]{event.get("id").asLong(), type.get("id").asLong()};
    }

    private String paid(String slug, long typeId, int quantity) {
        var reference = orders.place(new OrderRequest(slug, "Cliente", "+258 84 000 4444", null, List.of(new OrderRequest.Item(typeId, quantity)))).order().getReference();
        orders.markPaid(reference);
        return reference;
    }

    private JsonNode eventView(String slug) throws Exception {
        return body(mvc.perform(get("/api/events/" + slug)).andExpect(status().isOk())).get("ticketTypes").get(0);
    }

    @Test
    void refundVoidsTicketsReturnsSeatsAndLeavesRevenue() throws Exception {
        var ids = event("refund-night");
        var reference = paid("refund-night", ids[1], 4);
        assertThat(eventView("refund-night").get("available").asInt()).isEqualTo(6);
        var token = orders.markPaid(reference).order().getAccessToken();
        var code = body(mvc.perform(get("/api/tickets/" + token))).get("tickets").get(0).get("code").asText();

        var refunded = body(admin(post("/api/admin/orders/" + reference + "/refund"), Map.of("note", "Cliente desistiu; devolvido em numerário"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("REFUNDED"))));
        assertThat(refunded.get("refundNote").asText()).contains("devolvido");

        assertThat(eventView("refund-night").get("available").asInt()).isEqualTo(10); // seats are back on sale
        var gate = body(admin(post("/api/admin/check-in"), Map.of("eventId", ids[0], "code", code)).andExpect(status().isOk()));
        assertThat(gate.get("outcome").asText()).isEqualTo("VOID");
        var page = body(mvc.perform(get("/api/tickets/" + token)));
        assertThat(page.get("orderStatus").asText()).isEqualTo("REFUNDED");
        page.get("tickets").forEach(t -> assertThat(t.get("status").asText()).isEqualTo("VOID"));

        var report = body(admin(get("/api/admin/reports/events/" + ids[0]), null));
        assertThat(report.get("revenueMinor").asLong()).isZero();
        assertThat(report.get("ticketsSold").asInt()).isZero();
        assertThat(report.get("refundedMinor").asLong()).isEqualTo(400000);

        // refunding again changes nothing, and the freed seats can be sold to someone else
        admin(post("/api/admin/orders/" + reference + "/refund"), Map.of()).andExpect(status().isOk());
        assertThat(eventView("refund-night").get("available").asInt()).isEqualTo(10);
        paid("refund-night", ids[1], 10);
    }

    @Test
    void refundIsRefusedWhenUnpaidOrWhenSomeoneAlreadyEntered() throws Exception {
        var ids = event("no-refund-night");
        var pending = orders.place(new OrderRequest("no-refund-night", "Cliente", "+258 84 000 4444", null,
                List.of(new OrderRequest.Item(ids[1], 1)))).order().getReference();
        admin(post("/api/admin/orders/" + pending + "/refund"), Map.of()).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/orders/" + pending + "/refund")).andExpect(status().isUnauthorized());

        var reference = paid("no-refund-night", ids[1], 3);
        var token = orders.markPaid(reference).order().getAccessToken();
        var code = body(mvc.perform(get("/api/tickets/" + token))).get("tickets").get(0).get("code").asText();
        admin(post("/api/admin/check-in"), Map.of("eventId", ids[0], "code", code)).andExpect(jsonPath("$.outcome", is("ADMITTED")));

        admin(post("/api/admin/orders/" + reference + "/refund"), Map.of()).andExpect(status().isConflict());
        // nothing was changed by the refused refund: the other tickets still admit and the seats stay sold
        assertThat(body(mvc.perform(get("/api/tickets/" + token))).get("orderStatus").asText()).isEqualTo("PAID");
        var other = body(mvc.perform(get("/api/tickets/" + token))).get("tickets").get(1).get("code").asText();
        admin(post("/api/admin/check-in"), Map.of("eventId", ids[0], "code", other)).andExpect(jsonPath("$.outcome", is("ADMITTED")));
        assertThat(eventView("no-refund-night").get("available").asInt()).isEqualTo(6); // 3 paid + 1 pending still held
    }
}
