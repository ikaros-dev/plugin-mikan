package run.ikaros.plugin.mikan.qbittorrent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.exception.NotFoundException;
import run.ikaros.plugin.mikan.JsonUtils;
import run.ikaros.plugin.mikan.MikanPlugin;
import run.ikaros.plugin.mikan.qbittorrent.model.QbCategory;
import run.ikaros.plugin.mikan.qbittorrent.model.QbConfig;
import run.ikaros.plugin.mikan.qbittorrent.model.QbTorrentInfo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * @link <a href="https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-(qBittorrent-4.1)">WebUI-API-(qBittorrent-4.1)</a>
 */
@Slf4j
@Component
@Retryable
public class QbittorrentClient {
    private String category = DefaultConst.OPTION_QBITTORRENT_CATEGORY;
    private String categorySavePath = DefaultConst.OPTION_QBITTORRENT_CATEGORY_SAVE_PATH;
    private final RestTemplate restTemplate = new RestTemplate();
    private QbConfig config = new QbConfig();
    private HttpHeaders httpHeaders = new HttpHeaders();

    private final ReactiveCustomClient customClient;

    public QbittorrentClient(ReactiveCustomClient customClient) {
        this.customClient = customClient;
    }

    public interface API {
        String APP_VERSION = "/api/v2/app/version";
        String APP_API_VERSION = "/api/v2/app/webapiVersion";
        String AUTH = "/api/v2/auth/login";
        String TORRENTS_GET_ALL_CATEGORIES = "/api/v2/torrents/categories";
        String TORRENTS_CREATE_CATEGORY = "/api/v2/torrents/createCategory";
        String TORRENTS_EDIT_CATEGORY = "/api/v2/torrents/editCategory";
        String TORRENTS_REMOVE_CATEGORIES = "/api/v2/torrents/removeCategories";
        String TORRENTS_ADD = "/api/v2/torrents/add";
        String TORRENTS_INFO = "/api/v2/torrents/info";
        String TORRENTS_RENAME_FILE = "/api/v2/torrents/renameFile";
        String TORRENTS_RESUME = "/api/v2/torrents/resume";
        String TORRENTS_PAUSE = "/api/v2/torrents/pause";
        String TORRENTS_DELETE = "/api/v2/torrents/delete";
        String TORRENTS_RECHECK = "/api/v2/torrents/recheck";
        String TORRENTS_ADD_TAGS = "/api/v2/torrents/addTags";
    }

    public QbittorrentClient setHttpHeaders(HttpHeaders httpHeaders) {
        this.httpHeaders = httpHeaders;
        return this;
    }

    @Async
    public synchronized void refreshHttpHeadersCookies() {
        if (StringUtils.isNotBlank(config.getQbUrlPrefix())
            && StringUtils.isNotBlank(config.getQbUsername())
            && StringUtils.isNotBlank(config.getQbPassword())) {
            try {
                List<String> cookies =
                    getCookieByLogin(config.getQbUsername(), config.getQbPassword());
                this.httpHeaders.clear();
                this.httpHeaders.put(HttpHeaders.COOKIE, cookies);
            } catch (Exception exception) {
                log.warn("refresh qbittorrent options fail", exception);
            }

        }
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

    public List<String> getCookieByLogin(String username, String password) {
        if (StringUtils.isBlank(username)) {
            username = DefaultConst.OPTION_QBITTORRENT_USERNAME;
        }
        if (StringUtils.isBlank(password)) {
            password = DefaultConst.OPTION_QBITTORRENT_PASSWORD;
        }

        UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(getUrlPrefix() + API.AUTH)
            .queryParam("username", username)
            .queryParam("password", password)
            .build();

        ResponseEntity<String> responseEntity =
            restTemplate.getForEntity(uriComponents.toUriString(), String.class);
        if (responseEntity.getBody() != null && responseEntity.getBody().contains("Ok")) {
            HttpHeaders headers = responseEntity.getHeaders();
            List<String> cookies = headers.get("set-cookie");
            return cookies == null ? List.of() : cookies;
        }
        return List.of();
    }

    public String getCategory() {
        return category;
    }

    public String getUrlPrefix() {
        return config.getQbUrlPrefix();
    }

    public void initQbittorrentCategory() {
        try {
            boolean exist = getAllCategories().stream()
                .anyMatch(qbCategory -> qbCategory.getName().equalsIgnoreCase(category));
            if (!exist) {
                addNewCategory(category, categorySavePath);
                log.debug("add new qbittorrent category: {}, savePath: {}",
                    category, categorySavePath);
            }
        } catch (Exception exception) {
            log.warn("operate fail for add qbittorrent category: {}", category, exception);
        }
    }

    public String getApplicationVersion() {
        initQbittorrentCategory();
        ResponseEntity<String> responseEntity =
            restTemplate.exchange(getUrlPrefix() + API.APP_VERSION, HttpMethod.GET,
                new HttpEntity<>(null, httpHeaders), String.class);

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new QbittorrentRequestException("get app version fail");
        }
        return responseEntity.getBody();
    }

    public String getApiVersion() {
        initQbittorrentCategory();
        ResponseEntity<String> responseEntity
            = restTemplate.exchange(getUrlPrefix() + API.APP_API_VERSION,
            HttpMethod.GET, new HttpEntity<>(null, httpHeaders), String.class);

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new QbittorrentRequestException("get app version fail");
        }
        return responseEntity.getBody();
    }


    @SuppressWarnings("unchecked")
    public List<QbCategory> getAllCategories() {
        List<QbCategory> qbCategoryList = new ArrayList<>();

        ResponseEntity<HashMap> categoryMapResponseEntity =
            restTemplate.exchange(getUrlPrefix() + API.TORRENTS_GET_ALL_CATEGORIES,
                HttpMethod.GET, new HttpEntity<>(null, httpHeaders), HashMap.class);
        HashMap<String, Object> categoryMap = categoryMapResponseEntity.getBody();

        Assert.notNull(categoryMap, "'category map' must not null.");
        categoryMap.forEach((key, value) -> {
            Map<String, String> valueMap = (Map<String, String>) value;
            QbCategory category = new QbCategory();
            category.setName(valueMap.get("name"));
            category.setSavePath(valueMap.get("savePath"));
            Assert.notNull(category, "'category map' must not null.");
            if (!String.valueOf(key).equalsIgnoreCase(category.getName())) {
                log.warn("category map key != value's name, key={}, value={}",
                    key, value);
            }
            qbCategoryList.add(category);
        });

        return qbCategoryList;
    }

    public void addNewCategory(String category, String savePath) {
        Assert.hasText(category, "'category' must has text.");
        Assert.hasText(savePath, "'savePath' must has text.");
        final String url = getUrlPrefix() + API.TORRENTS_CREATE_CATEGORY;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        List<String> cookies = this.httpHeaders.get(HttpHeaders.COOKIE);
        headers.put(HttpHeaders.COOKIE, cookies == null ? List.of() : cookies);

        // body: category=CategoryName&savePath=/path/to/dir
        String body = "category=" + category + "&savePath=" + savePath;

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.POST, httpEntity, Object.class);
    }

    public void editCategory(String category, String savePath) {
        Assert.hasText(category, "'category' must has text.");
        Assert.hasText(savePath, "'savePath' must has text.");
        final String url = getUrlPrefix() + API.TORRENTS_EDIT_CATEGORY;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        List<String> cookies = this.httpHeaders.get(HttpHeaders.COOKIE);
        headers.put(HttpHeaders.COOKIE, cookies);

        // body: category=CategoryName&savePath=/path/to/dir
        String body = "category=" + category + "&savePath=" + savePath;

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.POST, httpEntity, Object.class);
    }

    public void removeCategories(List<String> categories) {
        Assert.hasText(category, "'category' must has text.");
        Assert.isTrue(!categories.isEmpty(), "'categories' must is empty.");
        final String url = getUrlPrefix() + API.TORRENTS_REMOVE_CATEGORIES;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        List<String> cookies = this.httpHeaders.get(HttpHeaders.COOKIE);
        headers.put(HttpHeaders.COOKIE, cookies == null ? List.of() : cookies);

        // body: categories=Category1%0ACategory2
        // categories can contain multiple categories separated by \n (%0A urlencoded)
        StringBuilder sb = new StringBuilder("categories=");
        for (int index = 0; index < categories.size(); index++) {
            sb.append(categories.get(index));
            if (index < (categories.size() - 1)) {
                sb.append("%0A");
            }
        }
        String body = sb.toString();

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.POST, httpEntity, Object.class);
    }

    /**
     * @param src                              url
     * @param savepath                         save path
     * @param category                         category
     * @param newName                          new torrent file name
     * @param skipChecking                     skip hash checking
     * @param statusIsPaused                   add torrents in the paused state
     * @param enableSequentialDownload         enable sequential download
     * @param prioritizeDownloadFirstLastPiece prioritize download first last piece,
     *                                         开启的话，经常会出现丢失文件的错误，不建议开启
     * @link <a href="https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-(qBittorrent-4.1)#add-new-torrent">WebUI-API-(qBittorrent-4.1)#add-new-torrent</a>
     */
    public synchronized void addTorrentFromURLs(String src,
                                                String savepath,
                                                String category,
                                                String newName,
                                                boolean skipChecking,
                                                boolean statusIsPaused,
                                                boolean enableSequentialDownload,
                                                boolean prioritizeDownloadFirstLastPiece) {
        Assert.notNull(src, "'src' must not null.");
        Assert.hasText(category, "'category' must has text.");
        final String url = getUrlPrefix() + API.TORRENTS_ADD;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        List<String> cookies = this.httpHeaders.get(HttpHeaders.COOKIE);
        headers.put(HttpHeaders.COOKIE, cookies == null ? List.of() : cookies);

        // This method can add torrents from server local file or from URLs.
        // http://, https://, magnet: and bc://bt/ links are supported.
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.put("urls", List.of(src));
        if (StringUtils.isNotBlank(savepath)) {
            body.put("savepath", List.of(savepath));
        }
        body.put("category", List.of(category));
        body.put("skip_checking", List.of(skipChecking ? "true" : "false"));
        body.put("paused", List.of(statusIsPaused ? "true" : "false"));
        if (StringUtils.isNotBlank(newName)) {
            body.put("rename", List.of(newName));
        }
        body.put("sequentialDownload", List.of(enableSequentialDownload ? "true" : "false"));
        body.put("firstLastPiecePrio",
            List.of(prioritizeDownloadFirstLastPiece ? "true" : "false"));

        HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.POST, httpEntity, Void.class);
    }

    public void addTorrentFromUrl(String url) {
        Assert.hasText(url, "'url' must has text.");
        addTorrentFromURLs(url, categorySavePath, category, null, true,
            false, false, false);
    }

    public void addTorrentFromUrl(String url, String newName) {
        Assert.hasText(url, "'url' must has text.");
        addTorrentFromURLs(url, categorySavePath, category, newName, true,
            false, false, false);
    }

    /**
     * @param filter   Filter torrent list by state. Allowed state filters: all, downloading,
     *                 seeding,completed, paused, active, inactive, resumed, stalled,
     *                 stalled_uploading,stalled_downloading, errored
     * @param category Get torrents with the given category (empty string means "without category";
     *                 no "category" parameter means "any category" <- broken until
     *                 #11748 is resolved). Remember to URL-encode the category name. For example,
     *                 My category becomes My%20category
     * @param limit    Limit the number of torrents returned
     * @param offset   Set offset (if less than 0, offset from end)
     * @param hashes   Filter by hashes. Can contain multiple hashes separated by |
     * @return qbittorrent torrent info list
     * @link <a href="https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-(qBittorrent-4.1)#get-torrent-list">WebUI-API-(qBittorrent-4.1)#get-torrent-list</a>
     * @see QbTorrentInfoFilter
     */
    public List<QbTorrentInfo> getTorrentList(QbTorrentInfoFilter filter,
                                              String category, String tags,
                                              Integer limit, Integer offset,
                                              String hashes) {
        final String url = getUrlPrefix() + API.TORRENTS_INFO;

        UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(url);
        if (filter != null) {
            urlBuilder.queryParam("filter", filter.getValue());
        }
        if (StringUtils.isNotBlank(category)) {
            urlBuilder.queryParam("category", category);
        }
        if (StringUtils.isNotBlank(tags)) {
            urlBuilder.queryParam("tags", tags);
        }
        if (limit != null && limit > 0) {
            urlBuilder.queryParam("limit", limit);
        }
        if (offset != null && offset > 0) {
            urlBuilder.queryParam("offset", offset);
        }
        if (StringUtils.isNotBlank(hashes)) {
            urlBuilder.queryParam("hashes", hashes);
        }

        ResponseEntity<ArrayList> originalListResponseEntity
            = restTemplate.exchange(urlBuilder.toUriString(),
            HttpMethod.GET, new HttpEntity<>(null, httpHeaders), ArrayList.class);
        ArrayList originalList = originalListResponseEntity.getBody();

        Assert.notNull(originalList, "'originalList' must not null.");

        List<QbTorrentInfo> qbTorrentInfoList = new ArrayList<>(originalList.size());
        for (Object o : originalList) {
            QbTorrentInfo qbTorrentInfo
                = JsonUtils.json2obj(JsonUtils.obj2Json(o), QbTorrentInfo.class);
            qbTorrentInfoList.add(qbTorrentInfo);
        }

        return qbTorrentInfoList;
    }

    public QbTorrentInfo getTorrent(String hash) {
        Assert.hasText(hash, "'hash' must has text.");
        List<QbTorrentInfo> torrentList = getTorrentList(null, null, null, null, null, hash);
        if (torrentList.isEmpty()) {
            throw new QbittorrentRequestException("torrent not found for hash=" + hash);
        }
        return torrentList.get(0);
    }

    /**
     * rename torrent file
     *
     * @param hash        The hash of the torrent
     * @param oldFileName The old file name(with postfix) of the torrent's file
     * @param newFileName The new file name(with postfix) to use for the file
     * @link <a href="https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-(qBittorrent-4.1)#rename-file">WebUI-API-(qBittorrent-4.1)#rename-file</a>
     */
    public void renameFile(String hash, String oldFileName,
                           String newFileName) {
        Assert.hasText(hash, "'hash' must has text.");
        Assert.hasText(oldFileName, "'oldFileName' must has text.");
        Assert.hasText(newFileName, "'newFileName' must has text.");
        final String url = getUrlPrefix() + API.TORRENTS_RENAME_FILE;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
        List<String> cookies = this.httpHeaders.get(HttpHeaders.COOKIE);
        headers.put(HttpHeaders.COOKIE, cookies == null ? List.of() : cookies);

        String body = "hash=" + hash + "&oldPath="
            + URLEncoder.encode(oldFileName, StandardCharsets.UTF_8)
            + "&newPath=" + URLEncoder.encode(newFileName, StandardCharsets.UTF_8);

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.POST, httpEntity, Void.class);
    }

    public void resume( String hashes) {
        Assert.hasText(hashes, "'hashes' must has text.");
        final String url = getUrlPrefix() + API.TORRENTS_RESUME;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
        List<String> cookies = this.httpHeaders.get(HttpHeaders.COOKIE);
        headers.put(HttpHeaders.COOKIE, cookies == null ? List.of() : cookies);

        String body = "hashes=" + hashes;

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.POST, httpEntity, Void.class);
    }

    public void pause(String hashes) {
        Assert.hasText(hashes, "'hashes' must has text.");
        final String url = getUrlPrefix() + API.TORRENTS_PAUSE;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
        List<String> cookies = this.httpHeaders.get(HttpHeaders.COOKIE);
        headers.put(HttpHeaders.COOKIE, cookies == null ? List.of() : cookies);

        String body = "hashes=" + hashes;

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.POST, httpEntity, Void.class);
    }

    public void recheck(String hashes) {
        Assert.hasText(hashes, "'hashes' must has text.");
        final String url = getUrlPrefix() + API.TORRENTS_RECHECK;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
        List<String> cookies = this.httpHeaders.get(HttpHeaders.COOKIE);
        headers.put(HttpHeaders.COOKIE, cookies == null ? List.of() : cookies);

        String body = "hashes=" + hashes;

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.POST, httpEntity, Void.class);
    }

    public void delete(String hashes, Boolean deleteFiles) {
        Assert.hasText(hashes, "'hashes' must has text.");
        final String url = getUrlPrefix() + API.TORRENTS_DELETE;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
        List<String> cookies = this.httpHeaders.get(HttpHeaders.COOKIE);
        headers.put(HttpHeaders.COOKIE, cookies == null ? List.of() : cookies);

        String body = "hashes=" + hashes
            + "&deleteFiles=" + (deleteFiles ? "true" : "false");

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.POST, httpEntity, Void.class);
    }

    public void tryToResumeAllMissingFilesErroredTorrents() {
        getTorrentList(QbTorrentInfoFilter.ERRORED,
            getCategory(), null, 100, null, null)
            .stream()
            .filter(qbTorrentInfo -> "missingFiles".equalsIgnoreCase(qbTorrentInfo.getState()))
            .forEach(qbTorrentInfo -> resume(qbTorrentInfo.getHash()));
    }

}
