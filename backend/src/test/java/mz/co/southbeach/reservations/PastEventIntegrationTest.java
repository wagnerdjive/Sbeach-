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

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=test-admin",
        "APP_ADMIN_PASSWORD=integration-test-password-17",
        "spring.datasource.url=jdbc:h2:mem:past-event-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class PastEventIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ResultActions admin(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : json.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Map<String, Object> event(String title) {
        var map = new LinkedHashMap<String, Object>();
        map.put("titlePt", title); map.put("dateTextPt", "11 de Maio de 2024"); map.put("descriptionPt", "Noite cubana");
        return map;
    }

    @Test
    void staffManageEventsAndAlbumsVisitorsReadThem() throws Exception {
        mvc.perform(get("/api/admin/past-events")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/past-events").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

        var created = body(admin(post("/api/admin/past-events"), event("Noite Cubana: Havana Édition")));
        long id = created.get("id").asLong();
        String slug = created.get("slug").asText();
        org.assertj.core.api.Assertions.assertThat(slug).isEqualTo("noite-cubana-havana-edition");
        // The same title again gets its own address.
        org.assertj.core.api.Assertions.assertThat(body(admin(post("/api/admin/past-events"), event("Noite Cubana: Havana Édition"))).get("slug").asText())
                .isEqualTo("noite-cubana-havana-edition-2");

        long first = body(admin(post("/api/admin/past-events/" + id + "/photos"), Map.of("imageUrl", "https://example.com/a.jpg"))).get("id").asLong();
        long second = body(admin(post("/api/admin/past-events/" + id + "/photos"), Map.of("imageUrl", "https://example.com/b.jpg"))).get("id").asLong();
        admin(post("/api/admin/past-events/" + id + "/photos"), Map.of("imageUrl", "http://insecure.example/c.jpg")).andExpect(status().isBadRequest());
        admin(post("/api/admin/past-events/" + id + "/photos"), Map.of("imageUrl", "/api/media/99999")).andExpect(status().isBadRequest());

        // Without a chosen cover the first photo stands in; the detail lists the album in order.
        mvc.perform(get("/api/past-events/" + slug)).andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl", is("https://example.com/a.jpg")))
                .andExpect(jsonPath("$.photos[0].imageUrl", is("https://example.com/a.jpg")))
                .andExpect(jsonPath("$.photoCount", is(2)))
                .andExpect(jsonPath("$.visible").doesNotExist());

        admin(put("/api/admin/past-events/" + id + "/photo-order"), Map.of("ids", java.util.List.of(second, first))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is((int) second)));
        admin(put("/api/admin/past-events/" + id + "/photo-order"), Map.of("ids", java.util.List.of(second))).andExpect(status().isBadRequest());

        // Hidden events are not public.
        var hidden = event("Evento escondido"); hidden.put("visible", false);
        long hiddenId = body(admin(post("/api/admin/past-events"), hidden)).get("id").asLong();
        var names = new ArrayList<String>();
        body(mvc.perform(get("/api/past-events"))).forEach(n -> names.add(n.get("titlePt").asText()));
        org.assertj.core.api.Assertions.assertThat(names).contains("Noite Cubana: Havana Édition").doesNotContain("Evento escondido");
        mvc.perform(get("/api/past-events/evento-escondido")).andExpect(status().isNotFound());

        var withCover = event("Noite Cubana: Havana Édition"); withCover.put("coverUrl", "https://example.com/capa.jpg");
        admin(put("/api/admin/past-events/" + id), withCover).andExpect(status().isOk()).andExpect(jsonPath("$.coverUrl", is("https://example.com/capa.jpg")));

        var ids = new ArrayList<Long>();
        body(admin(get("/api/admin/past-events"), null)).forEach(n -> ids.add(n.get("id").asLong()));
        Collections.reverse(ids);
        admin(put("/api/admin/past-event-order"), Map.of("ids", ids)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id", is(ids.get(0).intValue())));

        admin(delete("/api/admin/past-event-photos/" + first), null).andExpect(status().isNoContent());
        admin(delete("/api/admin/past-events/" + id), null).andExpect(status().isNoContent());
        admin(delete("/api/admin/past-events/" + hiddenId), null).andExpect(status().isNoContent());
        mvc.perform(get("/api/past-events/" + slug)).andExpect(status().isNotFound());
    }
}
