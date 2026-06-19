package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception;

public class StarkBankIntegrationException extends RuntimeException {

    public StarkBankIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
