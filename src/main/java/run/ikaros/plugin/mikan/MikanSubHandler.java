package run.ikaros.plugin.mikan;

import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import run.ikaros.api.core.subject.SubjectOperate;
import run.ikaros.plugin.mikan.qbittorrent.QbittorrentClient;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MikanSubHandler {
    private final MikanClient mikanClient;
    private final QbittorrentClient qbittorrentClient;
    private final SubjectOperate subjectOperate;

    public MikanSubHandler(MikanClient mikanClient, QbittorrentClient qbittorrentClient,
                           SubjectOperate subjectOperate) {
        this.mikanClient = mikanClient;
        this.qbittorrentClient = qbittorrentClient;
        this.subjectOperate = subjectOperate;
    }

    public Disposable generate() {
        // 订阅链接30分钟解析一次
        return Flux.interval(Duration.ofMinutes(30))
            .doOnEach(tick -> handle())
            .subscribe();
    }

    private void handle() {
        log.info("starting parse mikan my subscribe rss url from mikan config map.");
        List<MikanRssItem> mikanRssItemList = mikanClient.parseMikanMySubscribeRss();
        log.info("parse mikan my subscribe rss url to mikan rss item list, size: {} ",
            mikanRssItemList.size());

        log.info("adding torrents to qbittorrent.");
        for (MikanRssItem mikanRssItem : mikanRssItemList) {
            String mikanRssItemTitle = mikanRssItem.getTitle();
            log.info("start for each mikan rss item list for item title: {}",
                mikanRssItemTitle);

            qbittorrentClient.addTorrentFromUrl(mikanRssItem.getTorrentUrl(),
                mikanRssItemTitle);
            log.info("add to qbittorrent for torrent name: {}", mikanRssItemTitle);
        }
        log.info("end add torrents to qbittorrent.");


    }


}
