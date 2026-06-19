package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception;

public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
