package run.ikaros.plugin.mikan.exception;

public class RssOperateException extends RuntimeException {
    public RssOperateException() {
    }

    public RssOperateException(String message) {
        super(message);
    }

    public RssOperateException(String message, Throwable cause) {
        super(message, cause);
    }

    public RssOperateException(Throwable cause) {
        super(cause);
    }

    public RssOperateException(String message, Throwable cause, boolean enableSuppression,
                               boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
