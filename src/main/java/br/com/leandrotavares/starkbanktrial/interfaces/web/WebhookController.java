package br.com.leandrotavares.starkbanktrial.interfaces.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.leandrotavares.starkbanktrial.application.webhook.InvalidWebhookRequestException;
import br.com.leandrotavares.starkbanktrial.application.webhook.WebhookService;
import br.com.leandrotavares.starkbanktrial.application.webhook.dto.WebhookResponse;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.InvalidWebhookSignatureException;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.UnsupportedWebhookEventException;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/starkbank")
    public ResponseEntity<WebhookResponse> handleStarkBankWebhook(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(name = "Digital-Signature", required = false) String digitalSignature
    ) {
        if (digitalSignature == null || digitalSignature.isBlank()) {
            return badRequest("Digital-Signature header is required.");
        }

        try {
            return ResponseEntity.ok(webhookService.process(rawBody, digitalSignature));
        } catch (InvalidWebhookSignatureException | UnsupportedWebhookEventException |
                 InvalidWebhookRequestException exception) {
            return badRequest(exception.getMessage());
        }
    }

    private static ResponseEntity<WebhookResponse> badRequest(String message) {
        return ResponseEntity.badRequest().body(new WebhookResponse(
                "REJECTED",
                null,
                null,
                null,
                message
        ));
    }
}
