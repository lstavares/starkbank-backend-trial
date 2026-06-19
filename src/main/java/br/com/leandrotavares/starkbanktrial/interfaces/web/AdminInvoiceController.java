package br.com.leandrotavares.starkbanktrial.interfaces.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.leandrotavares.starkbanktrial.application.invoice.InvoiceService;
import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceBatchResponse;
import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceBatchResult;
import br.com.leandrotavares.starkbanktrial.application.invoice.dto.InvoiceResponse;

@RestController
@RequestMapping("/admin")
public class AdminInvoiceController {

    private final InvoiceService invoiceService;

    public AdminInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/invoices/issue-now")
    public InvoiceBatchResult issueInvoicesNow() {
        return invoiceService.issueManualBatch();
    }

    @GetMapping("/invoices")
    public List<InvoiceResponse> listInvoices() {
        return invoiceService.listLatestInvoices();
    }

    @GetMapping("/invoice-batches")
    public List<InvoiceBatchResponse> listInvoiceBatches() {
        return invoiceService.listLatestBatches();
    }
}
