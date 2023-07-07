package run.ikaros.plugin.mikan.qbittorrent;

public class QbittorrentRequestException extends RuntimeException {
    public QbittorrentRequestException() {
    }

    public QbittorrentRequestException(String message) {
        super(message);
    }

    public QbittorrentRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
