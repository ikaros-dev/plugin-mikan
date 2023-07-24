package run.ikaros.plugin.mikan;

import org.pf4j.RuntimeMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.ikaros.api.constant.AppConst;
import run.ikaros.api.core.file.FileOperate;
import run.ikaros.api.core.file.Folder;
import run.ikaros.api.core.file.FolderOperate;
import run.ikaros.api.core.subject.EpisodeFileOperate;
import run.ikaros.api.core.subject.Subject;
import run.ikaros.api.core.subject.SubjectOperate;
import run.ikaros.api.infra.properties.IkarosProperties;
import run.ikaros.api.infra.utils.FileUtils;
import run.ikaros.api.store.enums.FileType;
import run.ikaros.api.store.enums.SubjectSyncPlatform;
import run.ikaros.plugin.mikan.qbittorrent.QbTorrentInfoFilter;
import run.ikaros.plugin.mikan.qbittorrent.QbittorrentClient;
import run.ikaros.plugin.mikan.qbittorrent.model.QbTorrentInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

import static run.ikaros.api.constant.FileConst.DEFAULT_FOLDER_ROOT_ID;

@Slf4j
@Component
public class MikanSubHandler {
    private static final String QBITTORRENT_IMPORT_FOLDER_NAME = "downloads";
    private final MikanClient mikanClient;
    private final QbittorrentClient qbittorrentClient;
    private final SubjectOperate subjectOperate;
    private final FileOperate fileOperate;
    private final FolderOperate folderOperate;
    private final EpisodeFileOperate episodeFileOperate;
    private final IkarosProperties ikarosProperties;
    private RuntimeMode pluginRuntimeMode;

    public MikanSubHandler(MikanClient mikanClient, QbittorrentClient qbittorrentClient,
                           SubjectOperate subjectOperate, FileOperate fileOperate,
                           FolderOperate folderOperate, EpisodeFileOperate episodeFileOperate,
                           IkarosProperties ikarosProperties) {
        this.mikanClient = mikanClient;
        this.qbittorrentClient = qbittorrentClient;
        this.subjectOperate = subjectOperate;
        this.fileOperate = fileOperate;
        this.folderOperate = folderOperate;
        this.episodeFileOperate = episodeFileOperate;
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
                log.info("start for each mikan rss item list for item title: {}",
                    mikanRssItem.getTitle()))
            .flatMap(mikanRssItem -> {
                String mikanRssItemTitle = mikanRssItem.getTitle();
                qbittorrentClient.addTorrentFromUrl(mikanRssItem.getTorrentUrl(),
                    mikanRssItemTitle);
                log.info("add to qbittorrent for torrent name: [{}] and torrent url: [{}].",
                    mikanRssItemTitle, mikanRssItem.getTorrentUrl());

                QbTorrentInfo qbTorrentInfo = qbittorrentClient.getTorrentList(QbTorrentInfoFilter.ALL,
                        qbittorrentClient.getCategory(), null, null, null, null)
                    .stream().filter(torrentInfo -> mikanRssItemTitle.equals(torrentInfo.getName()))
                    .findFirst().orElse(null);
                String bgmTvSubjectId = Objects.isNull(qbTorrentInfo) ? "" : qbTorrentInfo.getTags();
                if(!StringUtils.hasText(bgmTvSubjectId)) {
                    // add subject map for torrentName
                    String animePageUrl =
                        mikanClient.getAnimePageUrlByEpisodePageUrl(mikanRssItem.getEpisodePageUrl());
                    String bgmTvSubjectPageUrl =
                        mikanClient.getBgmTvSubjectPageUrlByAnimePageUrl(animePageUrl);
                    if (!StringUtils.hasText(bgmTvSubjectPageUrl)) {
                        return Mono.empty();
                    }
                    int index = bgmTvSubjectPageUrl.lastIndexOf("/");
                    bgmTvSubjectId = bgmTvSubjectPageUrl.substring(index + 1);
                    if(Objects.nonNull(qbTorrentInfo) && StringUtils.hasText(qbTorrentInfo.getHash())) {
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

    private Mono<Subject> matchingSingleFile(Subject subject, String fileName) {
        String postfix = FileUtils.parseFilePostfix(fileName);
        FileType fileType = FileUtils.parseTypeByPostfix(postfix);
        log.debug("matching: subject: [{}]%n "
                + "fileName: [{}] postfix: [{}] fileType: [{}]" ,
            getSubjectName(subject), fileName, postfix, fileType);
        return fileOperate.findAllByNameLikeAndType(fileName, fileType)
            .collectList()
            .filter(files -> !files.isEmpty())
            .map(files -> files.get(0))
            .flatMap(file -> episodeFileOperate.batchMatching(subject.getId(),
                    new Long[] {file.getId()})
                .doOnSuccess(
                    unused -> log.debug("batch success for subject: [{}] and [{}].",
                        getSubjectName(subject), file.getName()))
            ).then(Mono.just(subject));
    }

    public Mono<Void> importQbittorrentFilesAndAddSubject() {
        return Mono.just(qbittorrentClient)
            .doOnNext(qc ->
                log.info("starting import qbittorrent files that has download finished..."))
            .flatMapMany(qc -> Flux.fromStream(qc.getTorrentList(QbTorrentInfoFilter.ALL,
                qc.getCategory(), null, null, null, null).stream()))
            .filter(qbTorrentInfo -> qbTorrentInfo.getProgress() == 1.0)
            .doOnNext(qbTorrentInfo ->
                log.info("start handle single torrent for content path: {}",
                    qbTorrentInfo.getContentPath()))
            .flatMap(qbTorrentInfo ->
                folderOperate.findByParentIdAndName(DEFAULT_FOLDER_ROOT_ID,
                        QBITTORRENT_IMPORT_FOLDER_NAME)
                    .switchIfEmpty(folderOperate.create(DEFAULT_FOLDER_ROOT_ID,
                        QBITTORRENT_IMPORT_FOLDER_NAME))
                    .flatMap(folder -> Mono.just(qbTorrentInfo)
                        .map(QbTorrentInfo::getContentPath)
                        .map(File::new)
                        .flatMap(file -> importFileByHardLinkRecursively(file, folder)))
                    .then(Mono.just(qbTorrentInfo)))

            .flatMap(torrentInfo -> Mono.just(
                    torrentInfo.getTags())
                .filter(StringUtils::hasText)
                .flatMap(bgmTvSubjectId -> subjectOperate.syncByPlatform(null,
                        SubjectSyncPlatform.BGM_TV, bgmTvSubjectId)
                    .flatMap(subject -> {
                        String contentPath = torrentInfo.getContentPath();
                        File torrentFile = new File(contentPath);
                        if(torrentFile.isFile()) {
                            String fileName = contentPath.substring(contentPath.lastIndexOf(File.separatorChar) + 1);
                            return matchingSingleFile(subject, fileName);
                        }

                        if(torrentFile.isDirectory()) {
                            File[] files = torrentFile.listFiles();
                            return Mono.justOrEmpty(files)
                                .flatMapMany(files1 -> Flux.fromStream(Arrays.stream(files1)))
                                .map(File::getName)
                                .flatMap(fileName -> matchingSingleFile(subject, fileName))
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

    private Mono<File> importFileByHardLinkRecursively(File torrentFile,
                                                       Folder folder) {
        return Mono.just(torrentFile)
            .filter(File::isFile)
            .flatMap(torrentContentFile -> {
                String fileName = torrentContentFile.getName();
                String postfix = FileUtils.parseFilePostfix(fileName);
                FileType fileType = FileUtils.parseTypeByPostfix(postfix);
                run.ikaros.api.core.file.File dbFile = new run.ikaros.api.core.file.File();
                dbFile.setName(fileName).setCanRead(true)
                    .setSize(torrentContentFile.length())
                    .setFolderId(folder.getId())
                    .setType(fileType);
                return fileOperate.findAllByNameLikeAndType(fileName, fileType)
                    .collectList()
                    .filter(List::isEmpty)
                    .<String>handle((files, sink) -> {
                        try {
                            sink.next(FileUtils.calculateFileHash(
                                FileUtils.convertToDataBufferFlux(torrentContentFile)));
                        } catch (IOException e) {
                            sink.error(new RuntimeException(e));
                        }
                    })
                    .doOnError(throwable -> log.error("calculate file md5 fail.", throwable))
                    .doOnNext(dbFile::setMd5)
                    .flatMap(fileOperate::existsByMd5)
                    .filter(exists -> !exists)
                    .map(exists -> FileUtils.buildAppUploadFilePath(
                        ikarosProperties.getWorkDir().toString(), postfix))
                    .map(File::new)
                    .flatMap(importFile -> {
                        String importFilePath = importFile.getAbsolutePath();
                        dbFile.setFsPath(importFilePath);
                        dbFile.setUrl(
                            FileUtils.path2url(importFilePath,
                                ikarosProperties.getWorkDir().toString()));
                        dbFile.setUpdateTime(LocalDateTime.now());
                        try {
                            Files.createLink(importFile.toPath(), torrentContentFile.toPath());
                            log.debug("hard link file success, target: [{}], exists: [{}].",
                                importFilePath, torrentContentFile.getAbsolutePath());
                        } catch (IOException e) {
                            log.error("link file fail, will use copy, exception: ", e);
                            try {
                                Files.copy(torrentContentFile.toPath(), importFile.toPath());
                                log.debug("copy link file success, target: [{}], exists: [{}].",
                                    importFilePath, torrentContentFile.getAbsolutePath());
                            } catch (IOException ex) {
                                return Mono.error(new RuntimeException(ex));
                            }
                        }
                        return Mono.just(importFile);
                    })
                    .doOnError(throwable -> log.error("link torrent file fail.", throwable))
                    .map(file -> dbFile)
                    ;

            })
            .flatMap(fileOperate::create)
            .doOnNext(dbFile ->
                log.info("import torrent file success for {}.",
                    dbFile.getName()))

            .then(Mono.just(torrentFile))
            .filter(File::isDirectory)
            .filter(file -> Objects.nonNull(file.listFiles()))
            .flatMapMany(torrentContentFile -> {
                String name = torrentContentFile.getName();
                return folderOperate.findByParentIdAndName(folder.getId(), name)
                    .switchIfEmpty(folderOperate.create(folder.getId(), name))
                    .flatMapMany(folder1 -> Flux.fromStream(Arrays.stream(
                            Objects.requireNonNull(torrentContentFile.listFiles())))
                        .flatMap(file -> importFileByHardLinkRecursively(file, folder1)));
            })
            .then(Mono.just(torrentFile));
    }


}
