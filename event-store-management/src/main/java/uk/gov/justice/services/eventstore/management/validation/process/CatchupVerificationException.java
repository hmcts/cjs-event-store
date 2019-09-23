package uk.gov.justice.services.eventstore.management.validation.process;

public class CatchupVerificationException extends RuntimeException {

    public CatchupVerificationException(final String message) {
        super(message);
    }

    public CatchupVerificationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
