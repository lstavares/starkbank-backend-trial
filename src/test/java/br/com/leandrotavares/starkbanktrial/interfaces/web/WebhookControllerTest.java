package br.com.leandrotavares.starkbanktrial.interfaces.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.leandrotavares.starkbanktrial.application.webhook.WebhookService;
import br.com.leandrotavares.starkbanktrial.application.webhook.dto.WebhookResponse;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.InvalidWebhookSignatureException;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    private static final String RAW_BODY = "{\"event\":{\"id\":\"evt_001\"}}";
    private static final String DIGITAL_SIGNATURE = "valid-signature";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookService webhookService;

    @Test
    void shouldDelegateRawBodyAndSignatureToService() throws Exception {
        when(webhookService.process(RAW_BODY, DIGITAL_SIGNATURE)).thenReturn(new WebhookResponse(
                "PROCESSED",
                "evt_001",
                "inv_001",
                "trf_001",
                "Transfer created successfully."
        ));

        mockMvc.perform(post("/webhooks/starkbank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Digital-Signature", DIGITAL_SIGNATURE)
                        .content(RAW_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.eventId").value("evt_001"))
                .andExpect(jsonPath("$.invoiceId").value("inv_001"))
                .andExpect(jsonPath("$.transferId").value("trf_001"));

        verify(webhookService).process(RAW_BODY, DIGITAL_SIGNATURE);
    }

    @Test
    void shouldReturnOkForDuplicateEvent() throws Exception {
        when(webhookService.process(RAW_BODY, DIGITAL_SIGNATURE)).thenReturn(new WebhookResponse(
                "DUPLICATE",
                "evt_001",
                "inv_001",
                null,
                "Event already processed."
        ));

        mockMvc.perform(post("/webhooks/starkbank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Digital-Signature", DIGITAL_SIGNATURE)
                        .content(RAW_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));
    }

    @Test
    void shouldReturnOkForSkippedEvent() throws Exception {
        when(webhookService.process(RAW_BODY, DIGITAL_SIGNATURE)).thenReturn(new WebhookResponse(
                "SKIPPED",
                "evt_001",
                "inv_001",
                null,
                "Invoice event is not paid and was skipped."
        ));

        mockMvc.perform(post("/webhooks/starkbank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Digital-Signature", DIGITAL_SIGNATURE)
                        .content(RAW_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));
    }

    @Test
    void shouldReturnOkForControlledFailure() throws Exception {
        when(webhookService.process(RAW_BODY, DIGITAL_SIGNATURE)).thenReturn(new WebhookResponse(
                "FAILED",
                "evt_001",
                "inv_001",
                null,
                "Unable to determine invoice fee."
        ));

        mockMvc.perform(post("/webhooks/starkbank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Digital-Signature", DIGITAL_SIGNATURE)
                        .content(RAW_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void shouldReturnBadRequestForInvalidSignature() throws Exception {
        when(webhookService.process(RAW_BODY, DIGITAL_SIGNATURE)).thenThrow(new InvalidWebhookSignatureException(
                "Invalid Stark Bank webhook signature.",
                null
        ));

        mockMvc.perform(post("/webhooks/starkbank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Digital-Signature", DIGITAL_SIGNATURE)
                        .content(RAW_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Invalid Stark Bank webhook signature."));
    }

    @Test
    void shouldReturnBadRequestForMissingSignatureHeader() throws Exception {
        mockMvc.perform(post("/webhooks/starkbank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RAW_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Digital-Signature header is required."));

        verifyNoInteractions(webhookService);
    }
}
