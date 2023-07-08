package run.ikaros.plugin.mikan.exception;

public class MikanRequestException extends RuntimeException {
    public MikanRequestException() {
    }

    public MikanRequestException(String message) {
        super(message);
    }

    public MikanRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
