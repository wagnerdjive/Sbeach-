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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=test-admin",
        "APP_ADMIN_PASSWORD=integration-test-password-17",
        "spring.datasource.url=jdbc:h2:mem:report-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.tickets.expiry-check-ms=3600000"
})
@AutoConfigureMockMvc
class ReportIntegrationTest {
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

    private long type(long eventId, String name, int price, int capacity) throws Exception {
        return body(admin(post("/api/admin/events/" + eventId + "/ticket-types"), Map.of("name", name, "priceMinor", price,
                "capacity", capacity, "maxPerOrder", 10)).andExpect(status().isCreated())).get("id").asLong();
    }

    private String order(String slug, String name, long typeId, int quantity) {
        return orders.place(new OrderRequest(slug, name, "+258 84 000 3333", null, List.of(new OrderRequest.Item(typeId, quantity)))).order().getReference();
    }

    @Test
    void reportCountsOnlyPaidOrdersAsRevenueAndShowsTheRest() throws Exception {
        var event = body(admin(post("/api/admin/events"), Map.of("slug", "report-night", "title", "Report Night", "location", "X",
                "startsAt", "2030-01-01T17:00:00Z", "status", "PUBLISHED")).andExpect(status().isCreated()));
        long eventId = event.get("id").asLong();
        long normal = type(eventId, "Normal", 100000, 100), vip = type(eventId, "VIP", 300000, 10);

        var paid1 = order("report-night", "Ana", normal, 3);   // 3 × 1000 = 3000 MZN
        var paid2 = order("report-night", "=HYPERLINK(\"http://evil\")", vip, 2); // 2 × 3000 = 6000 MZN
        order("report-night", "Pendente", normal, 4);          // held, not revenue
        var cancelled = order("report-night", "Cancelada", vip, 1);
        orders.markPaid(paid1);
        orders.markPaid(paid2);
        orders.cancel(cancelled);
        // one person already came in
        var token = orders.markPaid(paid1).order().getAccessToken();
        var code = body(mvc.perform(get("/api/tickets/" + token))).get("tickets").get(0).get("code").asText();
        admin(post("/api/admin/check-in"), Map.of("eventId", eventId, "code", code)).andExpect(status().isOk());

        var report = body(admin(get("/api/admin/reports/events/" + eventId), null).andExpect(status().isOk()));
        assertThat(report.get("paidOrders").asInt()).isEqualTo(2);
        assertThat(report.get("ticketsSold").asInt()).isEqualTo(5);
        assertThat(report.get("revenueMinor").asLong()).isEqualTo(900000);
        assertThat(report.get("admitted").asInt()).isEqualTo(1);

        var byType = report.get("byType");
        assertThat(byType.get(0).get("revenueMinor").asLong()).isEqualTo(300000);
        assertThat(byType.get(0).get("held").asInt()).isEqualTo(4);
        assertThat(byType.get(0).get("available").asInt()).isEqualTo(93);
        assertThat(byType.get(1).get("revenueMinor").asLong()).isEqualTo(600000);
        assertThat(report.get("byDay")).hasSize(1);
        assertThat(report.get("byDay").get(0).get("tickets").asInt()).isEqualTo(5);

        var statuses = new java.util.HashMap<String, Integer>();
        report.get("byStatus").forEach(s -> statuses.put(s.get("status").asText(), s.get("orders").asInt()));
        assertThat(statuses).containsEntry("PAID", 2).containsEntry("PENDING", 1).containsEntry("CANCELLED", 1);

        var summary = body(admin(get("/api/admin/reports/events"), null));
        assertThat(summary.findValuesAsText("title")).contains("Report Night");
    }

    @Test
    void csvExportIsStaffOnlyAndNeutralisesSpreadsheetFormulas() throws Exception {
        var event = body(admin(post("/api/admin/events"), Map.of("slug", "csv-night", "title", "Csv", "location", "X",
                "startsAt", "2030-01-01T17:00:00Z", "status", "PUBLISHED")).andExpect(status().isCreated()));
        long eventId = event.get("id").asLong();
        long type = type(eventId, "Normal", 150050, 50);
        order("csv-night", "=cmd|' /C calc'!A0", type, 2);
        order("csv-night", "Maria \"Mimi\", Silva", type, 1);

        mvc.perform(get("/api/admin/reports/events/" + eventId + "/orders.csv")).andExpect(status().isUnauthorized());
        var response = admin(get("/api/admin/reports/events/" + eventId + "/orders.csv"), null).andExpect(status().isOk()).andReturn().getResponse();
        var csv = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(response.getContentType()).startsWith("text/csv");
        assertThat(response.getHeader("Content-Disposition")).contains("attachment");
        assertThat(csv).contains("\"'=cmd|' /C calc'!A0\"");        // formula cell is defused
        assertThat(csv).contains("\"Maria \"\"Mimi\"\", Silva\"");  // quotes and commas escaped
        assertThat(csv).contains("\"3001.00\"");                     // 2 × 1500.50
        assertThat(csv).contains("\"+258 84 000 3333\"");            // phone keeps its leading +
        assertThat(csv.lines().count()).isEqualTo(3);                // header + 2 orders
    }
}
