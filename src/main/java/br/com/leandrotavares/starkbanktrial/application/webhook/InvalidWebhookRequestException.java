package br.com.leandrotavares.starkbanktrial.application.webhook;

public class InvalidWebhookRequestException extends RuntimeException {

    public InvalidWebhookRequestException(String message) {
        super(message);
    }

    public InvalidWebhookRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
