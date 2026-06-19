package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank;

import org.springframework.stereotype.Component;

import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.dto.ParsedInvoiceEvent;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.InvalidWebhookSignatureException;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.StarkBankIntegrationException;
import br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception.UnsupportedWebhookEventException;
import com.starkbank.Event;
import com.starkbank.Invoice;
import com.starkbank.error.InvalidSignatureError;

@Component
public class StarkBankWebhookParser {

    private final StarkBankProjectProvider projectProvider;
    private final StarkBankSdkGateway sdkGateway;

    public StarkBankWebhookParser(StarkBankProjectProvider projectProvider, StarkBankSdkGateway sdkGateway) {
        this.projectProvider = projectProvider;
        this.sdkGateway = sdkGateway;
    }

    public ParsedInvoiceEvent parseInvoiceEvent(String rawBody, String digitalSignature) {
        projectProvider.getProject();

        Event event;
        try {
            event = sdkGateway.parseEvent(rawBody, digitalSignature);
        } catch (InvalidSignatureError exception) {
            throw new InvalidWebhookSignatureException("Invalid Stark Bank webhook signature.", exception);
        } catch (Exception exception) {
            throw new StarkBankIntegrationException("Unable to parse Stark Bank webhook event.", exception);
        }

        if (!(event instanceof Event.InvoiceEvent invoiceEvent)) {
            String subscription = event == null ? null : event.subscription;
            throw new UnsupportedWebhookEventException(
                    "Unsupported Stark Bank webhook event subscription: " + subscription
            );
        }

        return toParsedInvoiceEvent(invoiceEvent);
    }

    private ParsedInvoiceEvent toParsedInvoiceEvent(Event.InvoiceEvent event) {
        Invoice.Log log = event.log;
        Invoice invoice = log == null ? null : log.invoice;
        Long fee = invoice == null ? null : toLong(invoice.fee);

        return new ParsedInvoiceEvent(
                event.id,
                event.subscription,
                invoice == null ? null : invoice.id,
                log == null ? null : log.id,
                log == null ? null : log.type,
                invoice == null ? null : invoice.status,
                invoice == null ? null : toLong(invoice.amount),
                fee,
                fee != null
        );
    }

    private static Long toLong(Number value) {
        if (value == null) {
            return null;
        }
        return value.longValue();
    }
}
