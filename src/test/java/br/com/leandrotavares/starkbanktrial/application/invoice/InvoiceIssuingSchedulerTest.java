package br.com.leandrotavares.starkbanktrial.application.invoice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceBatchResult;
import br.com.leandrotavares.starkbanktrial.config.InvoiceIssuingProperties;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchTriggerSource;

class InvoiceIssuingSchedulerTest {

    @Test
    void shouldNotCallServiceWhenDisabled() {
        InvoiceService invoiceService = Mockito.mock(InvoiceService.class);
        InvoiceIssuingProperties properties = new InvoiceIssuingProperties();
        properties.setEnabled(false);

        new InvoiceIssuingScheduler(invoiceService, properties).issueInvoices();

        verify(invoiceService, never()).issueScheduledBatchIfAllowed();
    }

    @Test
    void shouldCallServiceWhenEnabled() {
        InvoiceService invoiceService = Mockito.mock(InvoiceService.class);
        InvoiceIssuingProperties properties = new InvoiceIssuingProperties();
        properties.setEnabled(true);
        when(invoiceService.issueScheduledBatchIfAllowed()).thenReturn(new InvoiceBatchResult(
                "batch-001",
                1,
                "SUCCEEDED",
                BatchTriggerSource.SCHEDULED,
                1,
                List.of(),
                null
        ));

        new InvoiceIssuingScheduler(invoiceService, properties).issueInvoices();

        verify(invoiceService).issueScheduledBatchIfAllowed();
    }

    @Test
    void shouldHandleServiceErrorsWithoutThrowing() {
        InvoiceService invoiceService = Mockito.mock(InvoiceService.class);
        InvoiceIssuingProperties properties = new InvoiceIssuingProperties();
        properties.setEnabled(true);
        when(invoiceService.issueScheduledBatchIfAllowed()).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> new InvoiceIssuingScheduler(invoiceService, properties).issueInvoices());
    }
}
