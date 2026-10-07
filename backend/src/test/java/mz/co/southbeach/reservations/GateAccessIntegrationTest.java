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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=test-admin",
        "APP_ADMIN_PASSWORD=integration-test-password-17",
        "spring.datasource.url=jdbc:h2:mem:gate-access-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class GateAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderService orders;

    private ResultActions admin(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.with(httpBasic("test-admin", "integration-test-password-17")).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : json.writeValueAsString(body)));
    }

    private ResultActions as(String user, String password, MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.with(httpBasic(user, password)).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : json.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long[] event(String slug) throws Exception {
        var event = body(admin(post("/api/admin/events"), Map.of("slug", slug, "title", "Evento " + slug, "location", "South Beach",
                "startsAt", "2030-01-01T17:00:00Z", "status", "PUBLISHED")).andExpect(status().isCreated()));
        var type = body(admin(post("/api/admin/events/" + event.get("id").asLong() + "/ticket-types"), Map.of(
                "name", "Normal", "priceMinor", 50000, "capacity", 50, "maxPerOrder", 10)).andExpect(status().isCreated()));
        return new long[]{event.get("id").asLong(), type.get("id").asLong()};
    }

    private String ticketCode(String slug, long typeId) throws Exception {
        var ref = orders.place(new OrderRequest(slug, "Cliente", "+258 84 000 3333", null, List.of(new OrderRequest.Item(typeId, 1)))).order().getReference();
        var url = body(admin(post("/api/admin/orders/" + ref + "/mark-paid"), Map.of()).andExpect(status().isOk())).get("ticketsUrl").asText();
        var pass = body(mvc.perform(get("/api/tickets/" + url.substring(url.indexOf("t=") + 2))).andExpect(status().isOk()));
        return pass.get("tickets").get(0).get("code").asText();
    }

    private JsonNode createAccess(String label, Long eventId, Instant validUntil) throws Exception {
        var request = new LinkedHashMap<String, Object>();
        request.put("label", label); request.put("eventId", eventId); request.put("validUntil", validUntil == null ? null : validUntil.toString());
        return body(admin(post("/api/admin/gate-users"), request).andExpect(status().isCreated()));
    }

    @Test
    void doorStaffCanReadTicketsButNothingElse() throws Exception {
        var ev = event("gate-access-a");
        var code = ticketCode("gate-access-a", ev[1]);
        var access = createAccess("Porta principal", null, null);
        var user = access.get("username").asText();
        var pass = access.get("password").asText();
        assertThat(user).startsWith("porta-");
        assertThat(pass).hasSize(10);

        // The code is shown once: the list never repeats it.
        assertThat(body(admin(get("/api/admin/gate-users"), null).andExpect(status().isOk())).toString()).doesNotContain(pass);

        // What the door may do.
        as(user, pass, get("/api/gate/events"), null).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id==" + ev[0] + ")]").exists());
        as(user, pass, post("/api/gate/check-in/peek"), Map.of("eventId", ev[0], "code", code)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome", is("VALID")));
        as(user, pass, post("/api/gate/check-in"), Map.of("eventId", ev[0], "code", code)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome", is("ADMITTED")));
        as(user, pass, get("/api/gate/events/" + ev[0] + "/entry-stats"), null).andExpect(status().isOk()).andExpect(jsonPath("$.admitted", is(1)));
        as(user, pass, post("/api/gate/check-in/undo"), Map.of("eventId", ev[0], "code", code)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome", is("UNDONE")));

        // Everything else is closed to it: orders, reservations, content, the access list itself, the admin copies of the gate routes.
        as(user, pass, get("/api/admin/orders"), null).andExpect(status().isForbidden());
        as(user, pass, get("/api/admin/reservations"), null).andExpect(status().isForbidden());
        as(user, pass, get("/api/admin/gate-users"), null).andExpect(status().isForbidden());
        as(user, pass, post("/api/admin/gate-users"), Map.of("label", "intruso")).andExpect(status().isForbidden());
        as(user, pass, put("/api/admin/content"), Map.of("entries", Map.of())).andExpect(status().isForbidden());
        as(user, pass, post("/api/admin/check-in"), Map.of("eventId", ev[0], "code", code)).andExpect(status().isForbidden());
        as(user, pass, get("/api/admin/reports/events/" + ev[0] + "/orders.csv"), null).andExpect(status().isForbidden());

        // And the admin may use the gate routes too.
        admin(get("/api/gate/events"), null).andExpect(status().isOk());
        mvc.perform(get("/api/gate/events")).andExpect(status().isUnauthorized());
        as(user, "WRONGCODE1", get("/api/gate/events"), null).andExpect(status().isUnauthorized());
    }

    @Test
    void anAccessTiedToOneEventCannotTouchAnother() throws Exception {
        var a = event("gate-bound-a");
        var b = event("gate-bound-b");
        var codeB = ticketCode("gate-bound-b", b[1]);
        var access = createAccess("Só evento A", a[0], null);
        var user = access.get("username").asText(); var pass = access.get("password").asText();

        as(user, pass, get("/api/gate/events"), null).andExpect(status().isOk()).andExpect(jsonPath("$.length()", is(1))).andExpect(jsonPath("$[0].id", is((int) a[0])));
        as(user, pass, post("/api/gate/check-in/peek"), Map.of("eventId", b[0], "code", codeB)).andExpect(status().isForbidden());
        as(user, pass, post("/api/gate/check-in"), Map.of("eventId", b[0], "code", codeB)).andExpect(status().isForbidden());
        as(user, pass, get("/api/gate/events/" + b[0] + "/entry-stats"), null).andExpect(status().isForbidden());
        // The ticket of B was not touched by the refused attempts.
        admin(post("/api/gate/check-in/peek"), Map.of("eventId", b[0], "code", codeB)).andExpect(jsonPath("$.outcome", is("VALID")));
        // Scanning B's ticket at A is the normal "wrong event" refusal.
        as(user, pass, post("/api/gate/check-in"), Map.of("eventId", a[0], "code", codeB)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome", is("WRONG_EVENT")));
    }

    @Test
    void accessesCanBePausedExpireRenewAndBeRemoved() throws Exception {
        var access = createAccess("Turno da noite", null, null);
        long id = access.get("id").asLong();
        var user = access.get("username").asText(); var pass = access.get("password").asText();
        as(user, pass, get("/api/gate/events"), null).andExpect(status().isOk());

        var update = new LinkedHashMap<String, Object>();
        update.put("label", "Turno da noite"); update.put("eventId", null); update.put("validUntil", null); update.put("active", false);
        admin(put("/api/admin/gate-users/" + id), update).andExpect(status().isOk()).andExpect(jsonPath("$.usable", is(false)));
        as(user, pass, get("/api/gate/events"), null).andExpect(status().isUnauthorized());
        update.put("active", true);
        admin(put("/api/admin/gate-users/" + id), update).andExpect(status().isOk());
        as(user, pass, get("/api/gate/events"), null).andExpect(status().isOk());

        // A new code replaces the old one at once.
        var renewed = body(admin(post("/api/admin/gate-users/" + id + "/renew-code"), null).andExpect(status().isOk()));
        var newPass = renewed.get("password").asText();
        assertThat(newPass).isNotEqualTo(pass);
        as(user, pass, get("/api/gate/events"), null).andExpect(status().isUnauthorized());
        as(user, newPass, get("/api/gate/events"), null).andExpect(status().isOk());

        // An access that is already past its end date is refused, and one cannot be created in the past.
        var past = new LinkedHashMap<String, Object>();
        past.put("label", "Passado"); past.put("eventId", null); past.put("validUntil", Instant.now().minusSeconds(60).toString());
        admin(post("/api/admin/gate-users"), past).andExpect(status().isBadRequest());
        update.put("validUntil", Instant.now().plusSeconds(3600).toString());
        admin(put("/api/admin/gate-users/" + id), update).andExpect(status().isOk());
        as(user, newPass, get("/api/gate/events"), null).andExpect(status().isOk());

        admin(delete("/api/admin/gate-users/" + id), null).andExpect(status().isNoContent());
        as(user, newPass, get("/api/gate/events"), null).andExpect(status().isUnauthorized());
        admin(post("/api/admin/gate-users"), Map.of("label", "")).andExpect(status().isBadRequest());
        admin(post("/api/admin/gate-users"), Map.of("label", "Evento falso", "eventId", 99999)).andExpect(status().isBadRequest());
    }
}
