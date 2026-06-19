package br.com.leandrotavares.starkbanktrial.application.invoice;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceBatchResult;
import br.com.leandrotavares.starkbanktrial.config.InvoiceIssuingProperties;

@Component
public class InvoiceIssuingScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvoiceIssuingScheduler.class);

    private final InvoiceService invoiceService;
    private final InvoiceIssuingProperties properties;

    public InvoiceIssuingScheduler(InvoiceService invoiceService, InvoiceIssuingProperties properties) {
        this.invoiceService = invoiceService;
        this.properties = properties;
    }

    @Scheduled(fixedRateString = "${invoice.scheduler.interval-hours:3}", timeUnit = TimeUnit.HOURS)
    public void issueInvoices() {
        if (!properties.isEnabled()) {
            LOGGER.debug("Invoice issuing scheduler is disabled.");
            return;
        }

        try {
            InvoiceBatchResult result = invoiceService.issueScheduledBatchIfAllowed();
            LOGGER.info(
                    "Scheduled invoice issuing finished with status={} batchId={} sequenceNumber={}",
                    result.status(),
                    result.batchId(),
                    result.sequenceNumber()
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Scheduled invoice issuing failed.", exception);
        }
    }
}
