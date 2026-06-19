package br.com.leandrotavares.starkbanktrial.interfaces.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.leandrotavares.starkbanktrial.application.webhook.WebhookService;
import br.com.leandrotavares.starkbanktrial.application.webhook.dto.TransferRecordResponse;
import br.com.leandrotavares.starkbanktrial.application.webhook.dto.WebhookEventResponse;

@RestController
@RequestMapping("/admin")
public class AdminWebhookController {

    private final WebhookService webhookService;

    public AdminWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping("/webhook-events")
    public List<WebhookEventResponse> listWebhookEvents() {
        return webhookService.listLatestWebhookEvents();
    }

    @GetMapping("/transfers")
    public List<TransferRecordResponse> listTransfers() {
        return webhookService.listLatestTransfers();
    }
}
