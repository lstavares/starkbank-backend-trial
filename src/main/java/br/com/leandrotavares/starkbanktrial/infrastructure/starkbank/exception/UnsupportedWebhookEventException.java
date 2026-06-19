package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception;

public class UnsupportedWebhookEventException extends RuntimeException {

    public UnsupportedWebhookEventException(String message) {
        super(message);
    }
}
