package io.helix.android.rate;

/**
 * Created by furszy on 7/5/17.
 */
public class RequestHelixRateException extends Exception {
    public RequestHelixRateException(String message) {
        super(message);
    }

    public RequestHelixRateException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestHelixRateException(Exception e) {
        super(e);
    }
}
