package io.helix.android.rate;

/**
 * Created by furszy on 7/5/17.
 */
public class RequesthelixRateException extends Exception {
    public RequesthelixRateException(String message) {
        super(message);
    }

    public RequesthelixRateException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequesthelixRateException(Exception e) {
        super(e);
    }
}
