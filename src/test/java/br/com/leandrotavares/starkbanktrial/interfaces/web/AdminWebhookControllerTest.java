package br.com.leandrotavares.starkbanktrial.interfaces.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.leandrotavares.starkbanktrial.application.webhook.WebhookService;
import br.com.leandrotavares.starkbanktrial.application.webhook.dto.TransferRecordResponse;
import br.com.leandrotavares.starkbanktrial.application.webhook.dto.WebhookEventResponse;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.TransferStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.WebhookProcessingStatus;

@WebMvcTest(AdminWebhookController.class)
class AdminWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookService webhookService;

    @Test
    void shouldListLatestWebhookEvents() throws Exception {
        when(webhookService.listLatestWebhookEvents()).thenReturn(List.of(new WebhookEventResponse(
                "evt_001",
                "invoice",
                "inv_001",
                "log_001",
                "paid",
                WebhookProcessingStatus.PROCESSED,
                null,
                Instant.parse("2026-06-19T12:00:00Z"),
                Instant.parse("2026-06-19T12:01:00Z")
        )));

        mockMvc.perform(get("/admin/webhook-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt_001"))
                .andExpect(jsonPath("$[0].invoiceId").value("inv_001"))
                .andExpect(jsonPath("$[0].status").value("PROCESSED"));

        verify(webhookService).listLatestWebhookEvents();
    }

    @Test
    void shouldListLatestTransfers() throws Exception {
        when(webhookService.listLatestTransfers()).thenReturn(List.of(new TransferRecordResponse(
                "trf_001",
                "inv_001",
                "evt_001",
                "transfer-evt_001",
                1_000L,
                10L,
                990L,
                TransferStatus.SUCCEEDED,
                Instant.parse("2026-06-19T12:00:00Z"),
                null
        )));

        mockMvc.perform(get("/admin/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transferId").value("trf_001"))
                .andExpect(jsonPath("$[0].externalId").value("transfer-evt_001"))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"));

        verify(webhookService).listLatestTransfers();
    }
}
