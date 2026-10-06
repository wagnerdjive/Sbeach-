package mz.co.southbeach.reservations;

import com.fasterxml.jackson.databind.ObjectMapper;
import mz.co.southbeach.reservations.notification.ReminderScheduler;
import mz.co.southbeach.reservations.notification.SmsGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "APP_ADMIN_USERNAME=test-admin",
        "APP_ADMIN_PASSWORD=integration-test-password-17",
        "spring.datasource.url=jdbc:h2:mem:notifications-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Import(NotificationIntegrationTest.RecordingSms.class)
class NotificationIntegrationTest {
    static final List<String> SENT = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class RecordingSms {
        @Bean SmsGateway smsGateway() { return (phone, text) -> SENT.add(phone + "|" + text); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ReminderScheduler reminders;

    private String create(LocalDate date, String email) throws Exception {
        var payload = new java.util.HashMap<String, Object>(Map.of(
                "fullName", "Rita Notif", "phone", "+258 84 999 0000", "requestedDate", date.toString(),
                "requestedTime", "19:00", "partySize", 2, "venue", "RESTAURANT"));
        payload.put("email", email);
        var created = mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("reference").asText();
    }

    private void setStatus(String reference, String value) throws Exception {
        mvc.perform(patch("/api/admin/reservations/{r}/status", reference)
                .with(httpBasic("test-admin", "integration-test-password-17"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + value + "\"}")).andExpect(status().isOk());
    }

    @Test
    void confirmationAndCancellationSendSms() throws Exception {
        var reference = create(LocalDate.now().plusDays(20), "");
        setStatus(reference, "CONFIRMED");
        await().untilAsserted(() -> assertThat(SENT).anyMatch(m -> m.contains(reference) && m.contains("confirmada")));
        setStatus(reference, "CANCELLED");
        await().untilAsserted(() -> assertThat(SENT).anyMatch(m -> m.contains(reference) && m.contains("cancelada")));
    }

    @Test
    void reminderIsSentOnceForTomorrowsConfirmedReservation() throws Exception {
        var reference = create(LocalDate.now().plusDays(1), "rita@example.com");
        create(LocalDate.now().plusDays(1), null); // stays pending: no reminder
        setStatus(reference, "CONFIRMED");
        await().untilAsserted(() -> assertThat(SENT).anyMatch(m -> m.contains(reference)));
        SENT.clear();

        assertThat(reminders.sendDueReminders()).isEqualTo(1);
        assertThat(SENT).singleElement().satisfies(m -> assertThat(m).contains(reference).contains("lembrete"));
        assertThat(reminders.sendDueReminders()).isZero();
    }
}
