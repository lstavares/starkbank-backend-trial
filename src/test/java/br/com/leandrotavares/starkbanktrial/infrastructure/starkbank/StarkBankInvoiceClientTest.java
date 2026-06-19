package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.leandrotavares.starkbanktrial.config.StarkBankProperties;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreateInvoiceRequest;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.CreatedInvoiceResult;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.InvoiceDescriptionRequest;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.InvoicePaymentDetails;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.StarkBankIntegrationException;
import com.starkbank.Invoice;
import com.starkbank.Key;
import com.starkbank.Project;

class StarkBankInvoiceClientTest {

    @Test
    void shouldDelegateInvoiceCreationAndNormalizeResult() throws Exception {
        Project project = project();
        FakeProjectProvider projectProvider = new FakeProjectProvider(project);
        FakeSdkGateway sdkGateway = new FakeSdkGateway();

        Invoice createdInvoice = new Invoice();
        createdInvoice.id = "inv_001";
        createdInvoice.amount = 12_345L;
        createdInvoice.fee = 95;
        createdInvoice.status = "created";
        sdkGateway.createdInvoicesResult = List.of(createdInvoice);

        StarkBankInvoiceClient client = new StarkBankInvoiceClient(projectProvider, sdkGateway);
        List<CreatedInvoiceResult> results = client.createInvoices(List.of(new CreateInvoiceRequest(
                12_345L,
                "Ada Lovelace",
                "01234567890",
                List.of("batch-001", "scheduled"),
                List.of(new InvoiceDescriptionRequest("Order", "001"))
        )));

        Invoice sdkInvoice = (Invoice) sdkGateway.createdInvoicesRequest.get(0);

        assertThat(projectProvider.calls).isEqualTo(1);
        assertThat(sdkGateway.createInvoicesProject).isSameAs(project);
        assertThat(sdkInvoice.amount).isEqualTo(12_345L);
        assertThat(sdkInvoice.name).isEqualTo("Ada Lovelace");
        assertThat(sdkInvoice.taxId).isEqualTo("01234567890");
        assertThat(sdkInvoice.tags).containsExactly("batch-001", "scheduled");
        assertThat(sdkInvoice.descriptions)
                .singleElement()
                .satisfies(description -> {
                    assertThat(description.key).isEqualTo("Order");
                    assertThat(description.value).isEqualTo("001");
                });

        assertThat(results)
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.id()).isEqualTo("inv_001");
                    assertThat(result.amount()).isEqualTo(12_345L);
                    assertThat(result.fee()).isEqualTo(95L);
                    assertThat(result.status()).isEqualTo("created");
                });
    }

    @Test
    void shouldFetchPaymentDetailsWithoutAssumingUnknownFeeIsZero() throws Exception {
        Project project = project();
        FakeProjectProvider projectProvider = new FakeProjectProvider(project);
        FakeSdkGateway sdkGateway = new FakeSdkGateway();

        Invoice invoice = new Invoice();
        invoice.id = "inv_paid";
        invoice.amount = 20_000L;
        invoice.fee = null;
        invoice.status = "paid";
        sdkGateway.invoiceResult = invoice;

        Invoice.Payment payment = new Invoice.Payment();
        payment.amount = 20_000;
        payment.method = "pix";
        payment.endToEndId = "E123";
        sdkGateway.paymentResult = payment;

        StarkBankInvoiceClient client = new StarkBankInvoiceClient(projectProvider, sdkGateway);
        InvoicePaymentDetails details = client.getPaymentDetails("inv_paid");

        assertThat(sdkGateway.getInvoiceId).isEqualTo("inv_paid");
        assertThat(sdkGateway.getInvoiceProject).isSameAs(project);
        assertThat(sdkGateway.getPaymentInvoiceId).isEqualTo("inv_paid");
        assertThat(sdkGateway.getPaymentProject).isSameAs(project);
        assertThat(details.invoiceId()).isEqualTo("inv_paid");
        assertThat(details.invoiceAmount()).isEqualTo(20_000L);
        assertThat(details.invoiceFee()).isNull();
        assertThat(details.invoiceFeeKnown()).isFalse();
        assertThat(details.paymentAmount()).isEqualTo(20_000L);
        assertThat(details.paymentMethod()).isEqualTo("pix");
        assertThat(details.endToEndId()).isEqualTo("E123");
    }

    @Test
    void shouldTranslateSdkExceptionsWhenCreatingInvoices() throws Exception {
        FakeProjectProvider projectProvider = new FakeProjectProvider(project());
        FakeSdkGateway sdkGateway = new FakeSdkGateway();
        sdkGateway.createInvoicesException = new Exception("sdk unavailable");

        StarkBankInvoiceClient client = new StarkBankInvoiceClient(projectProvider, sdkGateway);

        assertThatThrownBy(() -> client.createInvoices(List.of(new CreateInvoiceRequest(
                10_000L,
                "Grace Hopper",
                "12345678909",
                List.of("batch-002"),
                List.of()
        )))).isInstanceOf(StarkBankIntegrationException.class)
                .hasMessage("Unable to create Stark Bank invoices.");
    }

    private static Project project() throws Exception {
        return new Project("sandbox", "project-id", Key.create().privatePem);
    }

    private static class FakeProjectProvider extends StarkBankProjectProvider {

        private final Project project;
        private int calls;

        FakeProjectProvider(Project project) {
            super(new StarkBankProperties());
            this.project = project;
        }

        @Override
        public synchronized Project getProject() {
            calls++;
            return project;
        }
    }

    private static class FakeSdkGateway extends StarkBankSdkGateway {

        private List<?> createdInvoicesRequest;
        private Project createInvoicesProject;
        private List<Invoice> createdInvoicesResult = List.of();
        private Exception createInvoicesException;
        private String getInvoiceId;
        private Project getInvoiceProject;
        private Invoice invoiceResult;
        private String getPaymentInvoiceId;
        private Project getPaymentProject;
        private Invoice.Payment paymentResult;

        @Override
        public List<Invoice> createInvoices(List<?> invoices, Project project) throws Exception {
            createdInvoicesRequest = invoices;
            createInvoicesProject = project;
            if (createInvoicesException != null) {
                throw createInvoicesException;
            }
            return createdInvoicesResult;
        }

        @Override
        public Invoice getInvoice(String invoiceId, Project project) {
            getInvoiceId = invoiceId;
            getInvoiceProject = project;
            return invoiceResult;
        }

        @Override
        public Invoice.Payment getInvoicePayment(String invoiceId, Project project) {
            getPaymentInvoiceId = invoiceId;
            getPaymentProject = project;
            return paymentResult;
        }
    }
}
