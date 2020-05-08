package org.apache.nifi.idbroker.domain;

public class RetryableCommunicationException extends RuntimeException {
    public RetryableCommunicationException(String message) {
        super(message);
    }

    public RetryableCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
