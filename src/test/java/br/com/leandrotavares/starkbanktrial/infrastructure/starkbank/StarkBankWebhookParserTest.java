package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import br.com.leandrotavares.starkbanktrial.config.StarkBankProperties;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.ParsedInvoiceEvent;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.InvalidWebhookSignatureException;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.UnsupportedWebhookEventException;
import com.starkbank.Event;
import com.starkbank.Invoice;
import com.starkbank.Key;
import com.starkbank.Project;
import com.starkbank.error.InvalidSignatureError;

class StarkBankWebhookParserTest {

    @Test
    void shouldParseInvoiceEvent() throws Exception {
        Project project = project();
        FakeProjectProvider projectProvider = new FakeProjectProvider(project);
        FakeSdkGateway sdkGateway = new FakeSdkGateway();
        sdkGateway.eventResult = invoiceEvent(125, 5);

        StarkBankWebhookParser parser = new StarkBankWebhookParser(projectProvider, sdkGateway);
        ParsedInvoiceEvent parsedEvent = parser.parseInvoiceEvent("raw-body", "signature");

        assertThat(projectProvider.calls).isEqualTo(1);
        assertThat(sdkGateway.rawBody).isEqualTo("raw-body");
        assertThat(sdkGateway.digitalSignature).isEqualTo("signature");
        assertThat(parsedEvent.eventId()).isEqualTo("evt_001");
        assertThat(parsedEvent.subscription()).isEqualTo("invoice");
        assertThat(parsedEvent.invoiceId()).isEqualTo("inv_001");
        assertThat(parsedEvent.invoiceLogId()).isEqualTo("log_001");
        assertThat(parsedEvent.logType()).isEqualTo("paid");
        assertThat(parsedEvent.status()).isEqualTo("paid");
        assertThat(parsedEvent.amount()).isEqualTo(125L);
        assertThat(parsedEvent.fee()).isEqualTo(5L);
        assertThat(parsedEvent.feeKnown()).isTrue();
    }

    @Test
    void shouldPreserveUnknownFee() throws Exception {
        FakeProjectProvider projectProvider = new FakeProjectProvider(project());
        FakeSdkGateway sdkGateway = new FakeSdkGateway();
        sdkGateway.eventResult = invoiceEvent(125, null);

        StarkBankWebhookParser parser = new StarkBankWebhookParser(projectProvider, sdkGateway);
        ParsedInvoiceEvent parsedEvent = parser.parseInvoiceEvent("raw-body", "signature");

        assertThat(parsedEvent.fee()).isNull();
        assertThat(parsedEvent.feeKnown()).isFalse();
    }

    @Test
    void shouldRejectInvalidSignature() throws Exception {
        FakeProjectProvider projectProvider = new FakeProjectProvider(project());
        FakeSdkGateway sdkGateway = new FakeSdkGateway();
        sdkGateway.parseException = new InvalidSignatureError("invalid signature");

        StarkBankWebhookParser parser = new StarkBankWebhookParser(projectProvider, sdkGateway);

        assertThatThrownBy(() -> parser.parseInvoiceEvent("raw-body", "bad-signature"))
                .isInstanceOf(InvalidWebhookSignatureException.class)
                .hasMessage("Invalid Stark Bank webhook signature.");
    }

    @Test
    void shouldRejectUnsupportedEvent() throws Exception {
        Event event = new Event();
        event.id = "evt_transfer";
        event.subscription = "transfer";

        FakeProjectProvider projectProvider = new FakeProjectProvider(project());
        FakeSdkGateway sdkGateway = new FakeSdkGateway();
        sdkGateway.eventResult = event;

        StarkBankWebhookParser parser = new StarkBankWebhookParser(projectProvider, sdkGateway);

        assertThatThrownBy(() -> parser.parseInvoiceEvent("raw-body", "signature"))
                .isInstanceOf(UnsupportedWebhookEventException.class)
                .hasMessage("Unsupported Stark Bank webhook event subscription: transfer");
    }

    private static Event.InvoiceEvent invoiceEvent(Integer amount, Integer fee) {
        Invoice invoice = new Invoice();
        invoice.id = "inv_001";
        invoice.amount = amount;
        invoice.fee = fee;
        invoice.status = "paid";

        Invoice.Log log = new Invoice.Log();
        log.id = "log_001";
        log.type = "paid";
        log.invoice = invoice;

        Event.InvoiceEvent event = new Event.InvoiceEvent();
        event.id = "evt_001";
        event.subscription = "invoice";
        event.log = log;
        return event;
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

        private String rawBody;
        private String digitalSignature;
        private Event eventResult;
        private Exception parseException;

        @Override
        public Event parseEvent(String rawBody, String digitalSignature) throws Exception {
            this.rawBody = rawBody;
            this.digitalSignature = digitalSignature;
            if (parseException != null) {
                throw parseException;
            }
            return eventResult;
        }
    }
}
