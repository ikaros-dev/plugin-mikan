package run.ikaros.plugin.mikan;


import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.infra.exception.NotFoundException;
import run.ikaros.api.infra.utils.SystemVarUtils;
import run.ikaros.api.plugin.event.PluginConfigMapUpdateEvent;
import run.ikaros.plugin.mikan.exception.MikanRequestException;
import run.ikaros.plugin.mikan.exception.RssOperateException;
import run.ikaros.plugin.mikan.qbittorrent.model.QbConfig;
import run.ikaros.plugin.mikan.utils.XmlUtils;

import java.io.*;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * @author li-guohao
 */
@Slf4j
@Component
public class MikanClient {
    private RestTemplate restTemplate = new RestTemplate();
    private static final String BASE_URL = "https://mikanime.tv";
    private QbConfig config = new QbConfig();
    private final ReactiveCustomClient customClient;
    private Proxy proxy = null;
    private final AtomicReference<Boolean> init = new AtomicReference<>(false);

    public MikanClient(ReactiveCustomClient customClient) {
        this.customClient = customClient;
    }

    public void setProxy(@Nullable Proxy proxy) {
        this.proxy = proxy;
    }

    @EventListener(PluginConfigMapUpdateEvent.class)
    public void updateConfig(PluginConfigMapUpdateEvent event) {
        if(Objects.isNull(event) || Objects.isNull(event.getConfigMap())
            || !MikanPlugin.NAME.equals(event.getConfigMap().getName())) {
            return;
        }
        Map<String, String> map = event.getConfigMap().getData();
        updateConfigByDataMap(map);
    }

    private void updateConfigByDataMap(Map<String, String> map) {
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
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() throws Exception {
        if(init.get()) {
            return;
        }
        customClient.findOne(ConfigMap.class, MikanPlugin.NAME)
            .onErrorResume(NotFoundException.class,
                e -> {
                    log.warn("not found plugin-mikan config map.", e);
                    return Mono.empty();
                })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(configMap -> {
                Map<String, String> map = configMap.getData();
                updateConfigByDataMap(map);
                init.set(true);
            });
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        Assert.notNull(restTemplate, "'restTemplate' must not null.");
        this.restTemplate = restTemplate;
    }

    @Retryable
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

    @Retryable
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

    @Retryable
    public String getAnimePageUrlBySearch(String keyword) {
        Assert.hasText(keyword, "'keyword' must has text.");
        log.debug("starting get anime page url by search keyword:{}", keyword);
        final String originalUrl = DefaultConst.MIKAN_URL + "/Home/Search?searchstr=";
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
            animePageUrl = DefaultConst.MIKAN_URL + bangumiUrl;
        } catch (Exception exception) {
            log.warn("get anime page url fail by search keyword:{}", keyword, exception);
        }
        log.debug("end find anime page url by keyword:{}  url:{}", keyword, animePageUrl);
        return animePageUrl;
    }

    @Retryable
    public List<MikanRssItem> parseMikanMySubscribeRss() {
        String mikanRss = config.getMikanRss();
        Assert.isTrue(StringUtils.isNotBlank(mikanRss), "'mikanRss' must not null.");
        // 1. 先下载RSS的XML文件到缓存目录
        String rssXmlFilePath = downloadRssXmlFile(mikanRss);
        // 2. 调用 DOM 库解析缓存文件
        List<MikanRssItem> mikanRssItemList = XmlUtils.parseMikanRssXmlFile(rssXmlFilePath);
        log.info("parse xml file get mikan rss item size: {}", mikanRssItemList.size());
        // 3. 删除对应的缓存文件
        if (StringUtils.isNotBlank(rssXmlFilePath)) {
            File file = new File(rssXmlFilePath);
            if (file.exists()) {
                file.delete();
                log.info("delete rss xml cache file for path: {}", rssXmlFilePath);
            }
        }
        return mikanRssItemList;
    }

    @Retryable
    public String downloadRssXmlFile(String url) {
        Assert.hasText(url, "'url' must has text.");
        String cacheFilePath = SystemVarUtils.getOsCacheDirPath(null) + File.separator
            + UUID.randomUUID().toString().replace("-", "") + ".xml";

        InputStream inputStream = null;
        BufferedInputStream bufferedInputStream = null;
        OutputStream outputStream = null;
        BufferedOutputStream bufferedOutputStream = null;
        try {
            URLConnection urlConnection;
            if (proxy == null) {
                urlConnection = new URL(url).openConnection();
            } else {
                urlConnection = new URL(url).openConnection(proxy);
            }
            urlConnection.connect();
            inputStream = urlConnection.getInputStream();
            bufferedInputStream = new BufferedInputStream(inputStream);

            File cacheFile = new File(cacheFilePath);
            if (!cacheFile.exists()) {
                cacheFile.createNewFile();
            }
            outputStream = Files.newOutputStream(cacheFile.toPath());
            bufferedOutputStream = new BufferedOutputStream(outputStream);

            byte[] buffer = new byte[128];
            int len;
            while ((len = bufferedInputStream.read(buffer)) != -1) {
                bufferedOutputStream.write(buffer, 0, len);
            }
            log.info("download rss xml file to cache path: {}", cacheFilePath);
        } catch (Exception e) {
            String msg = "fail write rss url xml file bytes to cache path: " + cacheFilePath;
            log.warn(msg, e);
            throw new RssOperateException(msg, e);
        } finally {
            try {
                if (bufferedOutputStream != null) {
                    bufferedOutputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                if (bufferedInputStream != null) {
                    bufferedInputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
                throw new RssOperateException(e);
            }
        }
        return cacheFilePath;
    }

}
