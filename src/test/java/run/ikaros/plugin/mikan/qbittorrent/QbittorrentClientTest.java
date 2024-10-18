package run.ikaros.plugin.mikan.qbittorrent;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import run.ikaros.plugin.mikan.DefaultConst;
import run.ikaros.plugin.mikan.qbittorrent.model.QbConfig;
import run.ikaros.plugin.mikan.qbittorrent.model.QbTorrentInfo;

import java.util.List;

class QbittorrentClientTest {

    // @Test
    void testGetCookieByPostLogin() {

        QbConfig config = new QbConfig();
        config.setQbUrlPrefix("http://192.168.9.10:8181");
        config.setQbUsername("admin");
        config.setQbPassword("adminadmin");

        QbittorrentClient qbittorrentClient = new QbittorrentClient(null);
        qbittorrentClient.setConfig(config);
        qbittorrentClient.getCookieByPostLogin(config.getQbUsername(), config.getQbPassword());
    }

    // @Test
    void testAddTorrentFromUrl() throws Exception {
        String name =
            "[ANi] 其实，我是最强的？（仅限港澳台地区） - 04 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4]";
        String url =
            "https://mikanime.tv/Download/20230723/b100ec4fb7b879c3dfa4e28a5e9889ba0ea0bb00.torrent";

        QbConfig config = new QbConfig();
        config.setQbUrlPrefix("http://localhost:50100/");

        QbittorrentClient qbittorrentClient = new QbittorrentClient(null);
        qbittorrentClient.setBaseSavePath("C:\\Users\\li-guohao\\Videos\\tests");
        qbittorrentClient.setConfig(config);

//        qbittorrentClient.addTorrentFromUrl(url, name);
        qbittorrentClient.addTorrentFromURLs(url, qbittorrentClient.getSavePath(),
            DefaultConst.OPTION_QBITTORRENT_CATEGORY,
            name, false, false, false, false);
    }

    // @Test
    void testGetApiVersion() {
        QbConfig config = new QbConfig();
        config.setQbUrlPrefix("http://localhost:50100/");

        QbittorrentClient qbittorrentClient = new QbittorrentClient(null);
        qbittorrentClient.setBaseSavePath("C:\\Users\\li-guohao\\Videos\\tests");
        qbittorrentClient.setConfig(config);
        System.out.println(qbittorrentClient.getApiVersion());
        System.out.println(qbittorrentClient.getApplicationVersion());
    }

    //@Test
    void testGetTorrents() {
        QbConfig config = new QbConfig();
        config.setQbUrlPrefix("http://192.168.9.10:8181");
//        config.setQbUsername("admin");
//        config.setQbPassword("adminadmin");

        QbittorrentClient qbittorrentClient = new QbittorrentClient(null);
        qbittorrentClient.setBaseSavePath("C:\\Users\\chivehao\\Videos\\Tests");
        qbittorrentClient.setConfig(config);

        List<QbTorrentInfo> torrentList =
            qbittorrentClient.getTorrentList(QbTorrentInfoFilter.ALL, null, null, null, null, null);

        Assertions.assertThat(torrentList).isNotNull();
        Assertions.assertThat(torrentList).isNotEmpty();
    }

     //@Test
    void addSingleTags() {
        QbConfig config = new QbConfig();
        config.setQbUrlPrefix("http://192.168.9.10:8181");
        config.setQbUsername("admin");
        config.setQbPassword("adminadmin");

        QbittorrentClient qbittorrentClient = new QbittorrentClient(null);
        qbittorrentClient.setConfig(config);
        qbittorrentClient.getCookieByPostLogin(config.getQbUsername(), config.getQbPassword());
        qbittorrentClient.setBaseSavePath("C:\\Users\\chivehao\\Videos\\Tests");

        qbittorrentClient.addSingleTags("a98ac1d535da3fd1e4995761e0d38af4ceffb8cc", "test");
    }
}