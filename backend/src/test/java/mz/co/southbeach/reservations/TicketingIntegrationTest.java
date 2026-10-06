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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=test-admin",
        "APP_ADMIN_PASSWORD=integration-test-password-17",
        "spring.datasource.url=jdbc:h2:mem:ticketing-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.tickets.expiry-check-ms=3600000"
})
@AutoConfigureMockMvc
class TicketingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderService orders;

    private ResultActions admin(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Creates a published event with one ticket type and returns [slug, typeId]. */
    private Object[] event(String slug, int capacity, int maxPerOrder) throws Exception {
        var created = body(admin(post("/api/admin/events"), Map.of("slug", slug, "title", "Sunset Sessions",
                "location", "South Beach Maputo", "startsAt", "2030-01-01T17:00:00Z", "status", "PUBLISHED"))
                .andExpect(status().isCreated()));
        var type = body(admin(post("/api/admin/events/" + created.get("id").asLong() + "/ticket-types"), Map.of(
                "name", "Normal", "priceMinor", 150000, "capacity", capacity, "maxPerOrder", maxPerOrder))
                .andExpect(status().isCreated()));
        return new Object[]{slug, type.get("id").asLong()};
    }

    private OrderRequest order(String slug, long typeId, int quantity) {
        return new OrderRequest(slug, "Joao Teste", "+258 84 000 1111", null, List.of(new OrderRequest.Item(typeId, quantity)));
    }

    @Test
    void adminManagesEventsAndPublicOnlySeesPublishedOnes() throws Exception {
        event("public-night", 5, 5);
        admin(post("/api/admin/events"), Map.of("slug", "secret-draft", "title", "Draft", "location", "X",
                "startsAt", "2030-02-01T17:00:00Z", "status", "DRAFT")).andExpect(status().isCreated());

        mvc.perform(post("/api/admin/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/events/secret-draft")).andExpect(status().isNotFound());
        mvc.perform(get("/api/events/public-night"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketTypes[0].priceMinor", is(150000)))
                .andExpect(jsonPath("$.ticketTypes[0].available", is(5)))
                .andExpect(jsonPath("$.ticketTypes[0].capacity").doesNotExist());
        admin(post("/api/admin/events"), Map.of("slug", "public-night", "title", "Dup", "location", "X",
                "startsAt", "2030-02-01T17:00:00Z", "status", "DRAFT")).andExpect(status().isConflict());
    }

    @Test
    void orderHoldsStockAndExpiryOrCancellationReleasesIt() throws Exception {
        var e = event("hold-night", 3, 3);
        var typeId = (Long) e[1];
        var placed = orders.place(order("hold-night", typeId, 3));
        assertThat(placed.order().getTotalMinor()).isEqualTo(450000);

        mvc.perform(get("/api/events/hold-night")).andExpect(jsonPath("$.ticketTypes[0].available", is(0)))
                .andExpect(jsonPath("$.ticketTypes[0].onSale", is(false)));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(order("hold-night", typeId, 1)))).andExpect(status().isConflict());

        admin(post("/api/admin/orders/" + placed.order().getReference() + "/cancel"), Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("CANCELLED")));
        mvc.perform(get("/api/events/hold-night")).andExpect(jsonPath("$.ticketTypes[0].available", is(3)));
    }

    @Test
    void paidOrderMovesHoldToSoldAndIsIdempotent() throws Exception {
        var typeId = (Long) event("paid-night", 4, 4)[1];
        var placed = orders.place(order("paid-night", typeId, 2));
        orders.markPaid(placed.order().getReference());
        orders.markPaid(placed.order().getReference());

        JsonNode type = null;
        for (var event : body(admin(get("/api/admin/events"), ""))) {
            if (event.get("slug").asText().equals("paid-night")) type = event.get("ticketTypes").get(0);
        }
        assertThat(type).isNotNull();
        assertThat(type.get("sold").asInt()).isEqualTo(2);
        assertThat(type.get("held").asInt()).isZero();
        admin(post("/api/admin/orders/" + placed.order().getReference() + "/cancel"), Map.of()).andExpect(status().isConflict());
        // capacity cannot drop below what is already sold
        admin(put("/api/admin/ticket-types/" + typeId), Map.of("name", "Normal", "priceMinor", 150000, "capacity", 1, "maxPerOrder", 4))
                .andExpect(status().isConflict());
        admin(put("/api/admin/ticket-types/" + typeId), Map.of("name", "Normal", "priceMinor", 200000, "capacity", 6, "maxPerOrder", 4))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacity", is(6))).andExpect(jsonPath("$.sold", is(2)));
    }

    @Test
    void invalidOrdersAreRejected() throws Exception {
        var typeId = (Long) event("rules-night", 20, 2)[1];
        var other = (Long) event("other-night", 20, 2)[1];
        for (var bad : List.of(order("rules-night", typeId, 3), order("rules-night", other, 1), order("missing", typeId, 1))) {
            mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad)))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Test
    void concurrentBuyersNeverOversell() throws Exception {
        var typeId = (Long) event("race-night", 10, 1)[1];
        var pool = Executors.newFixedThreadPool(20);
        var succeeded = new AtomicInteger();
        List<Callable<Void>> buyers = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            buyers.add(() -> {
                try {
                    orders.place(order("race-night", typeId, 1));
                    succeeded.incrementAndGet();
                } catch (RuntimeException soldOut) { /* expected for the losers */ }
                return null;
            });
        }
        pool.invokeAll(buyers);
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(10);
        mvc.perform(get("/api/events/race-night")).andExpect(jsonPath("$.ticketTypes[0].available", is(0)));
    }
}
