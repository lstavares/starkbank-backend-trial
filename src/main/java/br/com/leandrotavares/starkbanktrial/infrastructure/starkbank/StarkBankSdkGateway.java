package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank;

import java.util.List;

import org.springframework.stereotype.Component;

import com.starkbank.Event;
import com.starkbank.Invoice;
import com.starkbank.Project;
import com.starkbank.Transfer;

@Component
public class StarkBankSdkGateway {

    public List<Invoice> createInvoices(List<?> invoices, Project project) throws Exception {
        return Invoice.create(invoices, project);
    }

    public Invoice getInvoice(String invoiceId, Project project) throws Exception {
        return Invoice.get(invoiceId, project);
    }

    public Invoice.Payment getInvoicePayment(String invoiceId, Project project) throws Exception {
        return Invoice.payment(invoiceId, project);
    }

    public List<Transfer> createTransfers(List<?> transfers, Project project) throws Exception {
        return Transfer.create(transfers, project);
    }

    public Event parseEvent(String rawBody, String digitalSignature) throws Exception {
        return Event.parse(rawBody, digitalSignature);
    }
}
