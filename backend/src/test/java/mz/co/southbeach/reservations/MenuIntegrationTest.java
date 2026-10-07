package mz.co.southbeach.reservations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=test-admin",
        "APP_ADMIN_PASSWORD=integration-test-password-17",
        "spring.datasource.url=jdbc:h2:mem:menu-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class MenuIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ResultActions admin(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : json.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Map<String, Object> item(String name, Object price) {
        var map = new LinkedHashMap<String, Object>();
        map.put("venue", "RESTAURANT"); map.put("kind", "FOOD"); map.put("sectionPt", "Sushi"); map.put("namePt", name);
        map.put("priceCents", price);
        return map;
    }

    @Test
    void staffManageItemsAndVisitorsSeeOnlyVisibleOnes() throws Exception {
        mvc.perform(get("/api/admin/menu")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/menu").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

        long shown = body(admin(post("/api/admin/menu"), item("Sashimi misto", 85000))).get("id").asLong();
        var hiddenItem = item("Prato escondido", null);
        hiddenItem.put("visible", false);
        long hidden = body(admin(post("/api/admin/menu"), hiddenItem)).get("id").asLong();

        var publicNames = new ArrayList<String>();
        body(mvc.perform(get("/api/menu")).andExpect(status().isOk())).forEach(n -> {
            publicNames.add(n.get("namePt").asText());
            assertThat(n.has("visible")).isFalse();
        });
        assertThat(publicNames).contains("Sashimi misto").doesNotContain("Prato escondido");

        var changed = item("Sashimi misto", 90000);
        changed.put("descriptionPt", "Salmão, atum e peixe branco");
        admin(put("/api/admin/menu/" + shown), changed).andExpect(status().isOk())
                .andExpect(jsonPath("$.priceCents").value(90000));

        admin(delete("/api/admin/menu/" + hidden), null).andExpect(status().isNoContent());
        admin(delete("/api/admin/menu/" + hidden), null).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidItemsAndReordersEveryItem() throws Exception {
        admin(post("/api/admin/menu"), item("", 100)).andExpect(status().isBadRequest());
        admin(post("/api/admin/menu"), item("Prato", -5)).andExpect(status().isBadRequest());
        var wrongVenue = item("Prato", 100);
        wrongVenue.put("venue", "KITCHEN");
        admin(post("/api/admin/menu"), wrongVenue).andExpect(status().isBadRequest());

        admin(post("/api/admin/menu"), item("A", null)).andExpect(status().isCreated());
        admin(post("/api/admin/menu"), item("B", null)).andExpect(status().isCreated());
        var ids = new ArrayList<Long>();
        body(admin(get("/api/admin/menu"), null)).forEach(n -> ids.add(n.get("id").asLong()));
        Collections.reverse(ids);
        admin(put("/api/admin/menu-order"), Map.of("ids", ids)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ids.get(0)));
        admin(put("/api/admin/menu-order"), Map.of("ids", ids.subList(1, ids.size()))).andExpect(status().isBadRequest());
    }

    @Test
    void staffCreateOrderAndUseDivisions() throws Exception {
        long sushi = body(admin(post("/api/admin/menu-groups"), Map.of("venue", "BEACH", "namePt", "Divisão A"))).get("id").asLong();
        long other = body(admin(post("/api/admin/menu-groups"), Map.of("venue", "BEACH", "namePt", "Divisão B", "nameEn", "Division B"))).get("id").asLong();
        admin(post("/api/admin/menu-groups"), Map.of("venue", "BEACH", "namePt", "divisão a")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/menu-groups")).andExpect(status().isUnauthorized());

        var inB = item("Prato da B", 100);
        inB.put("venue", "BEACH"); inB.put("groupId", other);
        long itemId = body(admin(post("/api/admin/menu"), inB)).get("id").asLong();
        var inA = item("Prato da A", 100);
        inA.put("venue", "BEACH"); inA.put("groupId", sushi);
        admin(post("/api/admin/menu"), inA).andExpect(status().isCreated());

        // A division of another space is refused, and so is one that does not exist.
        var wrong = item("Errado", 100);
        wrong.put("venue", "SPORTS"); wrong.put("groupId", sushi);
        admin(post("/api/admin/menu"), wrong).andExpect(status().isBadRequest());
        wrong.put("groupId", 999999);
        admin(post("/api/admin/menu"), wrong).andExpect(status().isBadRequest());

        // Visitors see the divisions in the order staff chose: B first, then A.
        var beachIds = new ArrayList<Long>();
        body(admin(get("/api/admin/menu-groups"), null)).forEach(g -> { if (g.get("venue").asText().equals("BEACH")) beachIds.add(g.get("id").asLong()); });
        beachIds.remove(other); beachIds.add(0, other);
        admin(put("/api/admin/menu-group-order"), Map.of("venue", "BEACH", "ids", beachIds)).andExpect(status().isOk());
        var order = new ArrayList<String>();
        body(mvc.perform(get("/api/menu"))).forEach(n -> { if (n.has("groupPt")) order.add(n.get("groupPt").asText()); });
        assertThat(order.indexOf("Divisão B")).isGreaterThanOrEqualTo(0).isLessThan(order.indexOf("Divisão A"));
        admin(put("/api/admin/menu-group-order"), Map.of("venue", "BEACH", "ids", List.of(other))).andExpect(status().isBadRequest());

        admin(put("/api/admin/menu-groups/" + other), Map.of("venue", "BEACH", "namePt", "Divisão C")).andExpect(status().isOk())
                .andExpect(jsonPath("$.namePt").value("Divisão C"));

        // A division with items cannot be removed; an empty one can.
        admin(delete("/api/admin/menu-groups/" + other), null).andExpect(status().isBadRequest());
        admin(delete("/api/admin/menu/" + itemId), null).andExpect(status().isNoContent());
        long empty = body(admin(post("/api/admin/menu-groups"), Map.of("venue", "SPORTS", "namePt", "Vazia"))).get("id").asLong();
        admin(delete("/api/admin/menu-groups/" + empty), null).andExpect(status().isNoContent());
    }
}
