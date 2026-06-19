package br.com.leandrotavares.starkbanktrial.interfaces.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import br.com.leandrotavares.starkbanktrial.application.invoice.InvoiceService;
import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceBatchResponse;
import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceBatchResult;
import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceResponse;
import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceResult;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchStatus;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.BatchTriggerSource;
import br.com.leandrotavares.starkbanktrial.infrastructure.persistence.entity.enums.InvoiceStatus;

@WebMvcTest(AdminInvoiceController.class)
class AdminInvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceService invoiceService;

    @Test
    void shouldIssueManualBatch() throws Exception {
        when(invoiceService.issueManualBatch()).thenReturn(new InvoiceBatchResult(
                "batch-manual",
                1,
                "SUCCEEDED",
                BatchTriggerSource.MANUAL,
                null,
                List.of(new InvoiceResult(
                        "inv_001",
                        "batch-manual",
                        1_000L,
                        "Ada Lovelace",
                        "11144477735",
                        InvoiceStatus.CREATED,
                        null,
                        Instant.parse("2026-06-19T12:00:00Z")
                )),
                null
        ));

        mockMvc.perform(post("/admin/invoices/issue-now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value("batch-manual"))
                .andExpect(jsonPath("$.invoiceCount").value(1))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.triggerSource").value("MANUAL"))
                .andExpect(jsonPath("$.invoices[0].starkInvoiceId").value("inv_001"));

        verify(invoiceService).issueManualBatch();
    }

    @Test
    void shouldListLatestInvoices() throws Exception {
        when(invoiceService.listLatestInvoices()).thenReturn(List.of(new InvoiceResponse(
                "inv_001",
                "batch-001",
                1_000L,
                "Ada Lovelace",
                "11144477735",
                InvoiceStatus.CREATED,
                null,
                Instant.parse("2026-06-19T12:00:00Z"),
                null
        )));

        mockMvc.perform(get("/admin/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].starkInvoiceId").value("inv_001"))
                .andExpect(jsonPath("$[0].batchId").value("batch-001"))
                .andExpect(jsonPath("$[0].status").value("CREATED"));

        verify(invoiceService).listLatestInvoices();
    }

    @Test
    void shouldListLatestInvoiceBatches() throws Exception {
        when(invoiceService.listLatestBatches()).thenReturn(List.of(new InvoiceBatchResponse(
                "batch-001",
                BatchTriggerSource.SCHEDULED,
                1,
                8,
                BatchStatus.SUCCEEDED,
                Instant.parse("2026-06-19T12:00:00Z"),
                Instant.parse("2026-06-19T12:01:00Z"),
                null
        )));

        mockMvc.perform(get("/admin/invoice-batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].batchId").value("batch-001"))
                .andExpect(jsonPath("$[0].triggerSource").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"));

        verify(invoiceService).listLatestBatches();
    }
}
