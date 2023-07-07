package run.ikaros.plugin.mikan;


import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.exception.NotFoundException;
import run.ikaros.plugin.mikan.qbittorrent.model.QbConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * @author li-guohao
 */
@Slf4j
@Component
public class MikanClient {
    private RestTemplate restTemplate = new RestTemplate();
    private static final String BASE_URL = "https://mikanani.me";
    private QbConfig config = new QbConfig();
    private final ReactiveCustomClient customClient;

    public MikanClient(ReactiveCustomClient customClient) {
        this.customClient = customClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() throws Exception {
        customClient.findOne(ConfigMap.class, MikanPlugin.NAME)
            .onErrorResume(NotFoundException.class,
                e -> {
                    log.warn("not found plugin-mikan config map.", e);
                    return Mono.empty();
                })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(configMap -> {
                Map<String, String> map = configMap.getData();
                String mikanRss = map.get("mikanRss");
                if (StringUtils.isNotBlank(mikanRss)) {
                    config.setMikanRss(mikanRss);
                    log.debug("update mikan rss: {}", mikanRss);
                }
                String qbUrlPrefix = map.get("qbUrlPrefix");
                if (StringUtils.isNotBlank(qbUrlPrefix)) {
                    config.setQbUrlPrefix(qbUrlPrefix);
                    log.debug("update qbittorrent url prefix: {}", qbUrlPrefix);
                }
                String qbUsername = map.get("qbUsername");
                if (StringUtils.isNotBlank(qbUsername)) {
                    config.setQbUsername(qbUsername);
                    log.debug("update qbittorrent username: {}", qbUsername);
                }
                String qbPassword = map.get("qbPassword");
                if (StringUtils.isNotBlank(qbPassword)) {
                    config.setQbPassword(qbPassword);
                    log.debug("update qbittorrent password");
                }
            });
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        Assert.notNull(restTemplate, "'restTemplate' must not null.");
        this.restTemplate = restTemplate;
    }

    public String getAnimePageUrlByEpisodePageUrl(String episodePageUrl) {
        Assert.hasText(episodePageUrl, "'episodePageUrl' must has text.");
        log.debug("starting get anime page url by episode page url");
        byte[] bytes = restTemplate.getForObject(episodePageUrl, byte[].class);
        if (bytes == null) {
            throw new MikanRequestException("not found episode page url response data, "
                + "for episode page url: " + episodePageUrl);
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        Document document = Jsoup.parse(content);
        Element element = document.selectFirst("#sk-container .bangumi-title a");
        if (element == null) {
            throw new MikanRequestException(
                "not found element, for episode page url: " + episodePageUrl);
        }
        String href = element.attr("href");
        log.debug("completed get anime page url by episode page url");
        return BASE_URL + href;
    }

    public String getBgmTvSubjectPageUrlByAnimePageUrl(String animePageUrl) {
        Assert.hasText(animePageUrl, "'animePageUrl' must has text.");
        log.debug("starting get bgm tv subject page url by anime page url");
        byte[] bytes = restTemplate.getForObject(animePageUrl, byte[].class);
        if (bytes == null) {
            throw new MikanRequestException("not found anime page url response data, "
                + "for anime page url: " + animePageUrl);
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        Document document = Jsoup.parse(content);
        Elements elements = document.select("#sk-container .bangumi-info");
        Element targetElement = null;
        for (Element element : elements) {
            List<TextNode> textNodes = element.textNodes();
            if (textNodes.isEmpty()) {
                continue;
            }
            TextNode textNode = textNodes.get(0);
            String val = textNode.text();
            if (val.contains("Bangumi")) {
                targetElement = element.selectFirst("a");
            }
        }

        if (targetElement == null) {
            throw new MikanRequestException(
                "not found element, for anime page url: " + animePageUrl);
        }
        log.debug("completed get bgm tv subject page url by anime page url");
        return targetElement.attr("href");
    }

    public String getAnimePageUrlBySearch(String keyword) {
        Assert.hasText(keyword, "'keyword' must has text.");
        log.debug("starting get anime page url by search keyword:{}", keyword);
        final String originalUrl = "https://mikanani.me/Home/Search?searchstr=";
        final String url = originalUrl + keyword;

        byte[] bytes =
            restTemplate.getForObject(url,
                byte[].class);
        if (bytes == null) {
            throw new MikanRequestException("search anime page url exception for keyword "
                + keyword);
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        Document document = Jsoup.parse(content);
        String animePageUrl = null;
        try {
            String bangumiUrl = document
                .select("#sk-container .central-container ul li").get(0)
                .select("a").attr("href");
            animePageUrl = "https://mikanani.me" + bangumiUrl;
        } catch (Exception exception) {
            log.warn("get anime page url fail by search keyword:{}", keyword, exception);
        }
        log.debug("end find anime page url by keyword:{}  url:{}", keyword, animePageUrl);
        return animePageUrl;
    }

}
