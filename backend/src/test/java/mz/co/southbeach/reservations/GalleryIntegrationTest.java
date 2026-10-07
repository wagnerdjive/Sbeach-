package mz.co.southbeach.reservations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.Collections;
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
        "spring.datasource.url=jdbc:h2:mem:gallery-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class GalleryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ResultActions admin(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : json.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Map<String, Object> photo(String url, String caption) {
        var map = new LinkedHashMap<String, Object>();
        map.put("imageUrl", url); map.put("category", "EVENTS"); map.put("size", "NORMAL"); map.put("captionPt", caption);
        return map;
    }

    @Test
    void existingPhotosAreSeededAndPublic() throws Exception {
        mvc.perform(get("/api/gallery")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category", is("SPACES")))
                .andExpect(jsonPath("$[0].size", is("WIDE")))
                .andExpect(jsonPath("$[0].visible").doesNotExist());
        assertThat(body(mvc.perform(get("/api/gallery"))).size()).isGreaterThanOrEqualTo(9);
        mvc.perform(get("/api/admin/gallery")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/gallery").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void staffAddEditHideReorderAndRemovePhotos() throws Exception {
        var created = body(admin(post("/api/admin/gallery"), photo("https://example.com/new.jpg", "Nova foto")).andExpect(status().isCreated()));
        var id = created.get("id").asLong();
        assertThat(created.get("visible").asBoolean()).isTrue();

        var edit = photo("https://example.com/new.jpg", "Foto editada");
        edit.put("captionEn", "Edited photo"); edit.put("size", "TALL"); edit.put("visible", false);
        admin(put("/api/admin/gallery/" + id), edit).andExpect(status().isOk()).andExpect(jsonPath("$.size", is("TALL")));
        // hidden photos stay out of the public gallery but stay in the staff list
        assertThat(body(mvc.perform(get("/api/gallery"))).findValuesAsText("captionPt")).doesNotContain("Foto editada");
        assertThat(body(admin(get("/api/admin/gallery"), null)).findValuesAsText("captionPt")).contains("Foto editada");

        var ids = new ArrayList<Long>();
        body(admin(get("/api/admin/gallery"), null)).forEach(p -> ids.add(p.get("id").asLong()));
        Collections.reverse(ids);
        admin(put("/api/admin/gallery-order"), Map.of("ids", ids)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(ids.get(0).intValue())));
        admin(put("/api/admin/gallery-order"), Map.of("ids", ids.subList(1, ids.size()))).andExpect(status().isBadRequest());

        admin(delete("/api/admin/gallery/" + id), null).andExpect(status().isNoContent());
        admin(delete("/api/admin/gallery/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void unsafeImagesAreRejectedAndUploadsAreCleanedUp() throws Exception {
        for (var bad : List.of("http://insecure.example/a.jpg", "javascript:alert(1)", "data:image/png;base64,AAAA", "/api/media/999999", "https://x.com/a b.jpg")) {
            admin(post("/api/admin/gallery"), photo(bad, "x")).andExpect(status().isBadRequest());
        }
        var png = new byte[3000];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, png, 0, 8);
        var url = json.readTree(mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "a.png", "image/png", png))
                .with(httpBasic("test-admin", "integration-test-password-17"))).andReturn().getResponse().getContentAsString()).get("url").asText();
        var id = body(admin(post("/api/admin/gallery"), photo(url, "Carregada")).andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(get(url)).andExpect(status().isOk());

        admin(delete("/api/admin/gallery/" + id), null).andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound()); // the stored file went with the last photo using it
    }
}
