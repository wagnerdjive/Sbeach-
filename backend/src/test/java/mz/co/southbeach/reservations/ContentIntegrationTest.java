package mz.co.southbeach.reservations;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=test-admin",
        "APP_ADMIN_PASSWORD=integration-test-password-17",
        "spring.datasource.url=jdbc:h2:mem:content-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ContentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ResultActions save(Map<String, Object> entries) throws Exception {
        return mvc.perform(put("/api/admin/content").with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("entries", entries))));
    }

    private Map<String, Object> entry(String pt, String en) {
        var map = new LinkedHashMap<String, Object>();
        map.put("pt", pt);
        map.put("en", en);
        return map;
    }

    @Test
    void staffEditsAreServedPubliclyAndCanBeRestored() throws Exception {
        mvc.perform(put("/api/admin/content").contentType(MediaType.APPLICATION_JSON).content("{\"entries\":{}}"))
                .andExpect(status().isUnauthorized());

        save(Map.of("about.p.1", entry("Novo texto", "New text"), "contact.phone.href", entry("tel:+258841112222", null)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/content")).andExpect(status().isOk())
                .andExpect(jsonPath("$['about.p.1'].pt", is("Novo texto")))
                .andExpect(jsonPath("$['about.p.1'].en", is("New text")))
                .andExpect(jsonPath("$['contact.phone.href'].pt", is("tel:+258841112222")));

        save(Map.of("about.p.1", entry("", " "))).andExpect(status().isOk());
        mvc.perform(get("/api/content")).andExpect(jsonPath("$['about.p.1']").doesNotExist());
    }

    @Test
    void unsafeOrInvalidValuesAreRejectedAndNothingIsSaved() throws Exception {
        save(Map.of("x.href", entry("javascript:alert(1)", null))).andExpect(status().isBadRequest());
        save(Map.of("x.image", entry("http://insecure.example/a.png", null))).andExpect(status().isBadRequest());
        save(Map.of("x.image", entry("https://example.com/a\" onerror=\"x", null))).andExpect(status().isBadRequest());
        save(Map.of("Bad Key!", entry("a", null))).andExpect(status().isBadRequest());
        save(Map.of("x.p", entry("a".repeat(2001), null))).andExpect(status().isBadRequest());

        var mixed = new LinkedHashMap<String, Object>();
        mixed.put("good.p", entry("fine", null));
        mixed.put("bad.href", entry("javascript:1", null));
        save(mixed).andExpect(status().isBadRequest());
        mvc.perform(get("/api/content")).andExpect(jsonPath("$['good.p']").doesNotExist());
    }

    @Test
    void uploadedImagesAreStoredCheckedAndServed() throws Exception {
        var png = new byte[5000];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, png, 0, 8);

        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "a.png", "image/png", png)))
                .andExpect(status().isUnauthorized());
        var uploaded = mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "a.png", "image/png", png))
                        .with(httpBasic("test-admin", "integration-test-password-17")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var url = json.readTree(uploaded).get("url").asText();
        assertThat(url).startsWith("/api/media/");

        var served = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(served.getContentType()).isEqualTo("image/png");
        assertThat(served.getContentAsByteArray()).isEqualTo(png);
        save(Map.of("home.hero.bg", entry(url, null))).andExpect(status().isOk());

        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "a.png", "image/png", "<script>".getBytes()))
                .with(httpBasic("test-admin", "integration-test-password-17"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/media/999999")).andExpect(status().isNotFound());
    }
}
