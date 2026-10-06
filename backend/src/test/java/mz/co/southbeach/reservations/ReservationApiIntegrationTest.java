package mz.co.southbeach.reservations;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=test-admin",
        "APP_ADMIN_PASSWORD=integration-test-password-17",
        "spring.datasource.url=jdbc:h2:mem:reservations-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ReservationApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void publicRequestIsSavedAsPendingAndAdminCanConfirmIt() throws Exception {
        var payload = Map.of(
                "fullName", "Ana Cossa",
                "phone", "+258 84 123 4567",
                "requestedDate", LocalDate.now().plusDays(3).toString(),
                "requestedTime", "19:00",
                "partySize", 4,
                "venue", "RESTAURANT",
                "occasion", "Aniversário",
                "notes", "Mesa exterior, se possível"
        );

        var created = mvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn();

        var reference = objectMapper.readTree(created.getResponse().getContentAsString()).get("reference").asText();
        mvc.perform(get("/api/admin/reservations"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reservations").with(httpBasic("test-admin", "integration-test-password-17")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference", is(reference)))
                .andExpect(jsonPath("$.content[0].fullName", is("Ana Cossa")));
        mvc.perform(patch("/api/admin/reservations/{reference}/status", reference)
                        .with(httpBasic("test-admin", "integration-test-password-17"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    void invalidReservationIsRejected() throws Exception {
        var payload = Map.of(
                "fullName", "A",
                "phone", "123",
                "requestedDate", LocalDate.now().plusDays(2).toString(),
                "requestedTime", "19:00",
                "partySize", 0,
                "venue", "RESTAURANT"
        );
        mvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.fullName").exists());
    }
}
