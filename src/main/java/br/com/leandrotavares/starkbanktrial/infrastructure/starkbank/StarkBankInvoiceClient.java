package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank;

import java.util.List;

import org.springframework.stereotype.Component;

import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreateInvoiceRequest;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreatedInvoiceResult;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.InvoiceDescriptionRequest;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.InvoicePaymentDetails;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.StarkBankIntegrationException;
import com.starkbank.Invoice;
import com.starkbank.Project;

@Component
public class StarkBankInvoiceClient {

    private final StarkBankProjectProvider projectProvider;
    private final StarkBankSdkGateway sdkGateway;

    public StarkBankInvoiceClient(StarkBankProjectProvider projectProvider, StarkBankSdkGateway sdkGateway) {
        this.projectProvider = projectProvider;
        this.sdkGateway = sdkGateway;
    }

    public List<CreatedInvoiceResult> createInvoices(List<CreateInvoiceRequest> requests) {
        Project project = projectProvider.getProject();
        List<Invoice> invoices = requests.stream()
                .map(this::toSdkInvoice)
                .toList();

        try {
            return sdkGateway.createInvoices(invoices, project).stream()
                    .map(StarkBankInvoiceClient::toCreatedInvoiceResult)
                    .toList();
        } catch (Exception exception) {
            throw new StarkBankIntegrationException("Unable to create Stark Bank invoices.", exception);
        }
    }

    public InvoicePaymentDetails getPaymentDetails(String invoiceId) {
        Project project = projectProvider.getProject();

        try {
            Invoice invoice = sdkGateway.getInvoice(invoiceId, project);
            Invoice.Payment payment = sdkGateway.getInvoicePayment(invoiceId, project);
            return toPaymentDetails(invoiceId, invoice, payment);
        } catch (Exception exception) {
            throw new StarkBankIntegrationException("Unable to fetch Stark Bank invoice payment details.", exception);
        }
    }

    private Invoice toSdkInvoice(CreateInvoiceRequest request) {
        Invoice invoice = new Invoice();
        invoice.amount = request.amount();
        invoice.name = request.name();
        invoice.taxId = request.taxId();
        invoice.tags = toStringArray(request.tags());
        invoice.descriptions = toSdkDescriptions(request.descriptions());
        invoice.due = request.due();
        invoice.expiration = request.expiration();
        return invoice;
    }

    private List<Invoice.Description> toSdkDescriptions(List<InvoiceDescriptionRequest> descriptions) {
        if (descriptions == null) {
            return null;
        }

        return descriptions.stream()
                .map(description -> new Invoice.Description(description.key(), description.value()))
                .toList();
    }

    private static CreatedInvoiceResult toCreatedInvoiceResult(Invoice invoice) {
        return new CreatedInvoiceResult(
                invoice.id,
                toLong(invoice.amount),
                toLong(invoice.fee),
                invoice.status
        );
    }

    private static InvoicePaymentDetails toPaymentDetails(
            String invoiceId,
            Invoice invoice,
            Invoice.Payment payment
    ) {
        Long invoiceFee = invoice == null ? null : toLong(invoice.fee);
        return new InvoicePaymentDetails(
                invoice == null || invoice.id == null ? invoiceId : invoice.id,
                invoice == null ? null : invoice.status,
                invoice == null ? null : toLong(invoice.amount),
                invoiceFee,
                invoiceFee != null,
                payment == null ? null : toLong(payment.amount),
                payment == null ? null : payment.method,
                payment == null ? null : payment.endToEndId
        );
    }

    private static String[] toStringArray(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.toArray(String[]::new);
    }

    private static Long toLong(Number value) {
        if (value == null) {
            return null;
        }
        return value.longValue();
    }
}
