package br.com.leandrotavares.starkbanktrial.infrastructure.starkbank.exception;

public class StarkBankConfigurationException extends RuntimeException {

    public StarkBankConfigurationException(String message) {
        super(message);
    }

    public StarkBankConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
