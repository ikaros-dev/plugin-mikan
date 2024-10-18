package run.ikaros.plugin.mikan;

import reactor.test.StepVerifier;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.plugin.event.PluginConfigMapUpdateEvent;
import run.ikaros.plugin.mikan.qbittorrent.QbittorrentClient;
import run.ikaros.plugin.mikan.qbittorrent.model.QbConfig;

import java.util.Map;

class MikanSubHandlerTest {

    // @Test
    void parseMikanSubRssAndAddToQbittorrent() {
        final String mikanRss = "https://mikanime.tv/RSS/MyBangumi?token=";
        MikanClient mc = new MikanClient(null);
        ConfigMap map = new ConfigMap();
        map.setName(MikanPlugin.NAME);
        map.setData(Map.of("mikanRss", mikanRss));
        PluginConfigMapUpdateEvent event = new PluginConfigMapUpdateEvent(this, MikanPlugin.NAME, map);
        mc.updateConfig(event);

        QbConfig config = new QbConfig();
        config.setQbUrlPrefix("http://localhost:8181");
        config.setMikanRss(mikanRss);
//        config.setQbUsername("admin");
//        config.setQbPassword("adminadmin");


        QbittorrentClient qc = new QbittorrentClient(null);
        qc.setBaseSavePath("C:\\Users\\chivehao\\Videos\\Tests");
        qc.setConfig(config);

        MikanSubHandler mikanSubHandler = new MikanSubHandler(mc, qc, null, null, null, null);

        StepVerifier.create(mikanSubHandler.parseMikanSubRssAndAddToQbittorrent())
                .verifyComplete();

    }
}