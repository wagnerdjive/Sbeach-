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
}
