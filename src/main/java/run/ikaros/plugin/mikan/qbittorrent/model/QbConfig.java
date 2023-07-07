package run.ikaros.plugin.mikan.qbittorrent.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class QbConfig {
    private String mikanRss;
    /**
     * API前缀，例如：http://192.168.2.229:60101
     */
    private String qbUrlPrefix;
    private String qbUsername;
    private String qbPassword;
}
