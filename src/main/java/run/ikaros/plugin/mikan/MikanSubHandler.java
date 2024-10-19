package run.ikaros.plugin.mikan;

import org.pf4j.RuntimeMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.ikaros.api.constant.AppConst;
import run.ikaros.api.core.attachment.Attachment;
import run.ikaros.api.core.attachment.AttachmentConst;
import run.ikaros.api.core.attachment.AttachmentOperate;
import run.ikaros.api.core.attachment.AttachmentReferenceOperate;
import run.ikaros.api.core.subject.Subject;
import run.ikaros.api.core.subject.SubjectOperate;
import run.ikaros.api.core.tag.Tag;
import run.ikaros.api.core.tag.TagOperate;
import run.ikaros.api.infra.properties.IkarosProperties;
import run.ikaros.api.infra.utils.FileUtils;
import run.ikaros.api.store.enums.*;
import run.ikaros.plugin.mikan.qbittorrent.QbTorrentInfoFilter;
import run.ikaros.plugin.mikan.qbittorrent.QbittorrentClient;
import run.ikaros.plugin.mikan.qbittorrent.model.QbTorrentInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import run.ikaros.plugin.mikan.utils.StringMatchingUtils;

import static run.ikaros.api.constant.FileConst.DEFAULT_FOLDER_ROOT_ID;
import static run.ikaros.api.core.attachment.AttachmentConst.DOWNLOAD_DIRECTORY_ID;

@Slf4j
@Component
public class MikanSubHandler {
    private static final String QBITTORRENT_IMPORT_FOLDER_NAME = "downloads";
    private final MikanClient mikanClient;
    private final QbittorrentClient qbittorrentClient;
    private final SubjectOperate subjectOperate;
    private final AttachmentOperate attachmentOperate;
    private final AttachmentReferenceOperate attachmentReferenceOperate;
    private final TagOperate tagOperate;
    private final IkarosProperties ikarosProperties;
    private RuntimeMode pluginRuntimeMode;

    public MikanSubHandler(MikanClient mikanClient, QbittorrentClient qbittorrentClient,
                           SubjectOperate subjectOperate, AttachmentOperate attachmentOperate,
                           AttachmentReferenceOperate attachmentReferenceOperate, TagOperate tagOperate,
                           IkarosProperties ikarosProperties) {
        this.mikanClient = mikanClient;
        this.qbittorrentClient = qbittorrentClient;
        this.subjectOperate = subjectOperate;
        this.attachmentOperate = attachmentOperate;
        this.attachmentReferenceOperate = attachmentReferenceOperate;
        this.tagOperate = tagOperate;
        this.ikarosProperties = ikarosProperties;
    }

    public void setPluginRuntimeMode(RuntimeMode pluginRuntimeMode) {
        this.pluginRuntimeMode = pluginRuntimeMode;
    }

    public void init() {
        try {
            qbittorrentClient.init();
            qbittorrentClient.setBaseSavePath(ikarosProperties.getWorkDir()
                    .resolve(AppConst.CACHE_DIR_NAME).toString());
            mikanClient.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Disposable startParseMikanSubRssAndAddToQbittorrent() {
        // 订阅链接30分钟解析一次，插件开发者模式下3分钟一次
        return Flux.interval(Duration.ofMinutes(Objects.nonNull(pluginRuntimeMode) &&
                        RuntimeMode.DEVELOPMENT.equals(pluginRuntimeMode) ? 3 : 30))
                .flatMap(tick -> parseMikanSubRssAndAddToQbittorrent())
                .subscribeOn(Schedulers.newSingle("ParseMikanSubRssAndAddToQbittorrent", true))
                .subscribe();
    }

    public Disposable startImportQbittorrentFilesAndAddSubject() {
        // Qbittorrent 每5分钟查询一次，插件开发者模式下1分钟一次
        return Flux.interval(Duration.ofMinutes(Objects.nonNull(pluginRuntimeMode) &&
                        RuntimeMode.DEVELOPMENT.equals(pluginRuntimeMode) ? 1 : 5))
                .flatMap(tick -> importQbittorrentFilesAndAddSubject())
                .subscribeOn(Schedulers.newSingle("ImportQbittorrentFilesAndAddSubject", true))
                .subscribe();
    }

    public Mono<Void> parseMikanSubRssAndAddToQbittorrent() {
        return Mono.just(mikanClient)
                .doOnNext(mc ->
                        log.info("starting parse mikan my subscribe rss url from mikan config map."))
                .flatMapMany(mc -> Flux.fromStream(mc.parseMikanMySubscribeRss().stream()))
                .doOnNext(mikanRssItem ->
                        log.debug("start for each mikan rss item list for item title: {}",
                                mikanRssItem.getTitle()))
                .parallel()
                .flatMap(mikanRssItem -> {
                    String mikanRssItemTitle = mikanRssItem.getTitle();
                    qbittorrentClient.addTorrentFromUrl(mikanRssItem.getTorrentUrl(),
                            mikanRssItemTitle);
                    log.debug("add to qbittorrent for torrent name: [{}] and torrent url: [{}].",
                            mikanRssItemTitle, mikanRssItem.getTorrentUrl());

                    boolean qbIsNull = true;
                    QbTorrentInfo qbTorrentInfo = null;
                    long start = System.currentTimeMillis();
                    while (qbIsNull) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            log.error(e.getMessage(), e);
                        }
                        qbTorrentInfo =
                                qbittorrentClient.getTorrentList(QbTorrentInfoFilter.ALL,
                                                qbittorrentClient.getCategory(), null, null, null, null)
                                        .stream()
                                        .filter(torrentInfo -> StringMatchingUtils.isSimilar(mikanRssItemTitle, torrentInfo.getName(), 0.6)
                                                || mikanRssItemTitle.equals(torrentInfo.getName()))
                                        .findFirst().orElse(null);
                        if (qbTorrentInfo != null && StringUtils.hasText(qbTorrentInfo.getHash())) {
                            qbIsNull = false;
                        }
                        if (System.currentTimeMillis() - start > 5000) {
                            log.error("fail for qbittorrentClient.getTorrentList(), mikanItem: {}", mikanRssItem);
                            break;
                        }
                    }
                    long end = System.currentTimeMillis();
                    // log.debug("qbittorrentClient.getTorrentList {}ms", end - start);

                    String bgmTvSubjectId = qbTorrentInfo.getTags();
                    if (!StringUtils.hasText(bgmTvSubjectId)) {
                        // add subject map for torrentName
                        String animePageUrl =
                                mikanClient.getAnimePageUrlByEpisodePageUrl(
                                        mikanRssItem.getEpisodePageUrl());
                        String bgmTvSubjectPageUrl =
                                mikanClient.getBgmTvSubjectPageUrlByAnimePageUrl(animePageUrl);
                        if (!StringUtils.hasText(bgmTvSubjectPageUrl)) {
                            return Mono.empty();
                        }
                        int index = bgmTvSubjectPageUrl.lastIndexOf("/");
                        bgmTvSubjectId = bgmTvSubjectPageUrl.substring(index + 1);
                        // log.debug("bgmTvSubjectId and qbTorrentInfo" + "bgmTvSubjectId: {} \nqbTorrentInfo: {}", bgmTvSubjectId, qbTorrentInfo);
                        if (StringUtils.hasText(qbTorrentInfo.getHash())) {
                            qbittorrentClient.addSingleTags(qbTorrentInfo.getHash(), bgmTvSubjectId);
                            log.debug("add tag for torrent: {}", mikanRssItemTitle);
                        }
                    }
                    return subjectOperate.syncByPlatform(null, SubjectSyncPlatform.BGM_TV,
                            bgmTvSubjectId);
                })
                .doOnError(throwable -> log.error("parse mikan sub rss item fail.", throwable))
                .doOnComplete(() -> {
                    // 如果新添加的种子文件状态是缺失文件，则需要再恢复下
                    qbittorrentClient.tryToResumeAllMissingFilesErroredTorrents();
                    log.info("end parse mikan my subscribe rss url.");
                })
                .then();
    }

    private Mono<Subject> matchingSingleFile(Subject subject, String fileName, Long parentId) {
        // log.debug("matching: subject: [{}]%n "
        //         + "fileName: [{}] postfix: [{}] fileType: [{}]" ,
        //     getSubjectName(subject), fileName, postfix, fileType);
        return attachmentOperate.findByTypeAndParentIdAndName(AttachmentType.File, parentId,
                        fileName)
                .map(Attachment::getId)
                .flatMap(attId -> tagOperate.create(Tag.builder()
                        .createTime(LocalDateTime.now())
                        .type(TagType.ATTACHMENT)
                        .masterId(attId)
                        .name("subject:" + subject.getId())
                        .userId(-1L)
                        .build()))
                .flatMap(tag -> attachmentReferenceOperate
                        .matchingAttachmentsAndSubjectEpisodes(
                                subject.getId(), new Long[]{tag.getMasterId()}, true))
                .then(Mono.just(subject));
    }

    public Mono<Void> importQbittorrentFilesAndAddSubject() {
        return Mono.just(qbittorrentClient)
                .doOnNext(qc ->
                        log.info("starting import qbittorrent files that has download finished..."))
                .flatMapMany(qc -> Flux.fromStream(qc.getTorrentList(QbTorrentInfoFilter.ALL,
                        qc.getCategory(), null, null, null, null).stream()))
                .filter(qbTorrentInfo -> qbTorrentInfo.getProgress() == 1.0)
                .doOnNext(qbTorrentInfo ->
                        log.debug("start handle single torrent for content path: {}",
                                qbTorrentInfo.getContentPath()))
                .flatMap(qbTorrentInfo ->
                        importFileByHardLinkRecursively(qbTorrentInfo.getContentPath(),
                                DOWNLOAD_DIRECTORY_ID)
                                .then(Mono.just(qbTorrentInfo))
                )

                .flatMap(torrentInfo -> Mono.just(
                                torrentInfo.getTags())
                        .filter(StringUtils::hasText)
                        .flatMap(bgmTvSubjectId -> subjectOperate.syncByPlatform(null,
                                        SubjectSyncPlatform.BGM_TV, bgmTvSubjectId)
                                .flatMap(subject -> {
                                    String contentPath = torrentInfo.getContentPath();
                                    File torrentFile = new File(contentPath);
                                    if (torrentFile.isFile()) {
                                        String fileName = contentPath.substring(
                                                contentPath.lastIndexOf(File.separatorChar) + 1);
                                        return matchingSingleFile(subject, fileName, DOWNLOAD_DIRECTORY_ID);
                                    }

                                    if (torrentFile.isDirectory()) {
                                        File[] files = torrentFile.listFiles();
                                        return Mono.justOrEmpty(files)
                                                .flatMapMany(files1 -> Flux.fromStream(Arrays.stream(files1)))
                                                .map(File::getName)
                                                .flatMap(fileName -> attachmentOperate.findByTypeAndParentIdAndName(
                                                                AttachmentType.Directory, DOWNLOAD_DIRECTORY_ID,
                                                                torrentFile.getName())
                                                        .map(Attachment::getId)
                                                        .flatMap(attId -> matchingSingleFile(subject, fileName, attId)))
                                                .then(Mono.just(subject));
                                    }
                                    return Mono.just(subject);
                                })
                        )
                )
                .doOnError(throwable -> log.error("handle single torrent fail.", throwable))
                .doOnComplete(
                        () -> log.info("end import qbittorrent files that has download finished."))
                .then();
    }

    private static String getSubjectName(Subject subject) {
        String name = subject.getNameCn();
        if (!StringUtils.hasText(name)) {
            name = subject.getName();
        }
        return name;
    }

    private Mono<Void> importFileByHardLinkRecursively(String qbittorrentContentPath,
                                                       Long parentId) {
        File qbtorrentFile = new File(qbittorrentContentPath);
        if (!qbtorrentFile.exists()) {
            return Mono.empty();
        }

        if (qbtorrentFile.isDirectory()) {
            File[] files = qbtorrentFile.listFiles();
            if (Objects.isNull(files)) {
                return Mono.empty();
            }
            return attachmentOperate.save(Attachment.builder()
                            .type(AttachmentType.Directory)
                            .parentId(parentId)
                            .name(qbtorrentFile.getName())
                            .updateTime(LocalDateTime.now())
                            .build())
                    .checkpoint("SaveDirAttachment")
                    .flatMapMany(attachment -> Flux.fromArray(files)
                            .flatMap(file -> importFileByHardLinkRecursively(
                                    file.getAbsolutePath(), attachment.getId())))
                    .then();
        }

        // qbittorrent is file.
        String fileName = qbtorrentFile.getName();
        return attachmentOperate.existsByParentIdAndName(parentId, fileName)
                .filter(exists -> !exists)
                .map(exists -> Attachment.builder()
                        .parentId(parentId).name(fileName).size(qbtorrentFile.length())
                        .updateTime(LocalDateTime.now()).type(AttachmentType.File)
                        .build())
                .flatMap(attachment -> creatHardLinkOrCopy(
                        qbittorrentContentPath, attachment, qbtorrentFile))
                .flatMap(attachmentOperate::save)
                .doOnError(throwable -> log.error("link torrent file fail.", throwable))
                .then();
    }

    private Mono<Attachment> creatHardLinkOrCopy(String qbittorrentContentPath,
                                                 Attachment attachment, File qbtorrentFile) {
        String fileName = qbtorrentFile.getName();
        String postfix = FileUtils.parseFilePostfix(fileName);
        String uploadFilePath = FileUtils.buildAppUploadFilePath(
                ikarosProperties.getWorkDir().toString(), postfix);
        File uploadFile = new File(uploadFilePath);

        attachment.setType(AttachmentType.File);
        attachment.setName(fileName);
        attachment.setFsPath(uploadFilePath);
        attachment.setUrl(FileUtils.path2url(uploadFilePath,
                ikarosProperties.getWorkDir().toString()));
        attachment.setUpdateTime(LocalDateTime.now());

        try {
            Files.createLink(uploadFile.toPath(), qbtorrentFile.toPath());
            log.debug("hard link file success, target: [{}], exists: [{}].",
                    uploadFilePath, qbittorrentContentPath);
            return Mono.just(attachment);
        } catch (IOException e) {
            log.error("link file fail, will use copy, exception: ", e);
            try {
                Files.copy(qbtorrentFile.toPath(), uploadFile.toPath());
                log.debug("copy link file success, target: [{}], exists: [{}].",
                        uploadFilePath, qbittorrentContentPath);
                return Mono.just(attachment);
            } catch (IOException ex) {
                return Mono.error(new RuntimeException(ex));
            }
        }
    }


}
