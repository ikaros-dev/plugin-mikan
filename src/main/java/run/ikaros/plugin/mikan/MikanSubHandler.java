package run.ikaros.plugin.mikan;

import org.pf4j.RuntimeMode;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import run.ikaros.api.core.file.FileOperate;
import run.ikaros.api.core.file.Folder;
import run.ikaros.api.core.file.FolderOperate;
import run.ikaros.api.core.subject.SubjectOperate;
import run.ikaros.api.infra.properties.IkarosProperties;
import run.ikaros.api.infra.utils.FileUtils;
import run.ikaros.api.store.enums.FileType;
import run.ikaros.plugin.mikan.qbittorrent.QbTorrentInfoFilter;
import run.ikaros.plugin.mikan.qbittorrent.QbittorrentClient;
import run.ikaros.plugin.mikan.qbittorrent.model.QbTorrentInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
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
    private final IkarosProperties ikarosProperties;
    private RuntimeMode pluginRuntimeMode;

    public MikanSubHandler(MikanClient mikanClient, QbittorrentClient qbittorrentClient,
                           SubjectOperate subjectOperate, FileOperate fileOperate,
                           FolderOperate folderOperate, IkarosProperties ikarosProperties) {
        this.mikanClient = mikanClient;
        this.qbittorrentClient = qbittorrentClient;
        this.subjectOperate = subjectOperate;
        this.fileOperate = fileOperate;
        this.folderOperate = folderOperate;
        this.ikarosProperties = ikarosProperties;
    }

    public void setPluginRuntimeMode(RuntimeMode pluginRuntimeMode) {
        this.pluginRuntimeMode = pluginRuntimeMode;
    }

    private void init() {
        try {
            qbittorrentClient.init(ikarosProperties.getWorkDir().toString());
            mikanClient.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Disposable startParseMikanSubRssAndAddToQbittorrent() {
        init();
        // 订阅链接30分钟解析一次，插件开发者模式下3分钟一次
        return Flux.interval(Duration.ofMinutes(Objects.nonNull(pluginRuntimeMode) &&
                RuntimeMode.DEVELOPMENT.equals(pluginRuntimeMode) ? 3 : 30))
            .doOnEach(tick -> parseMikanSubRssAndAddToQbittorrent())
            .subscribeOn(Schedulers.newSingle("ParseMikanSubRssAndAddToQbittorrent", true))
            .subscribe();
    }

    public Disposable startImportQbittorrentFilesAndAddSubject() {
        init();
        // Qbittorrent 每5分钟查询一次，插件开发者模式下1分钟一次
        return Flux.interval(Duration.ofMinutes(Objects.nonNull(pluginRuntimeMode) &&
                RuntimeMode.DEVELOPMENT.equals(pluginRuntimeMode) ? 1 : 5))
            .doOnEach(tick -> importQbittorrentFilesAndAddSubject())
            .subscribeOn(Schedulers.newSingle("ImportQbittorrentFilesAndAddSubject", true))
            .subscribe();
    }

    public void parseMikanSubRssAndAddToQbittorrent() {
        log.info("starting parse mikan my subscribe rss url from mikan config map.");
        List<MikanRssItem> mikanRssItemList = mikanClient.parseMikanMySubscribeRss();
        log.info("parse mikan my subscribe rss url to mikan rss item list, size: {} ",
            mikanRssItemList.size());

        log.info("adding torrents to qbittorrent.");
        for (MikanRssItem mikanRssItem : mikanRssItemList) {
            String mikanRssItemTitle = mikanRssItem.getTitle();
            log.info("start for each mikan rss item list for item title: {}",
                mikanRssItemTitle);

            qbittorrentClient.addTorrentFromUrl(mikanRssItem.getTorrentUrl(), mikanRssItemTitle);
            log.info("add to qbittorrent for torrent name: [{}] and torrent url: [{}].",
                mikanRssItemTitle, mikanRssItem.getTorrentUrl());
        }
        // 如果新添加的种子文件状态是缺失文件，则需要再恢复下
        qbittorrentClient.tryToResumeAllMissingFilesErroredTorrents();
        log.info("end add torrents to qbittorrent. size: {}", mikanRssItemList.size());
    }

    public void importQbittorrentFilesAndAddSubject() {
        log.info("starting import qbittorrent files that has download finished...");
        List<QbTorrentInfo> torrentList = qbittorrentClient.getTorrentList(QbTorrentInfoFilter.ALL,
            qbittorrentClient.getCategory(), null, null, null, null);

        List<QbTorrentInfo> downloadProcessFinishTorrentList
            = torrentList.stream()
            .filter(qbTorrentInfo -> qbTorrentInfo.getProgress() == 1.0)
            .toList();

        for (QbTorrentInfo qbTorrentInfo : downloadProcessFinishTorrentList) {
            AtomicReference<Folder> importFolder = new AtomicReference<>();
            folderOperate.findByParentIdAndName(DEFAULT_FOLDER_ROOT_ID,
                    QBITTORRENT_IMPORT_FOLDER_NAME)
                .switchIfEmpty(folderOperate.create(DEFAULT_FOLDER_ROOT_ID,
                    QBITTORRENT_IMPORT_FOLDER_NAME))
                .subscribe(importFolder::set);
            while (Objects.isNull(importFolder.get())) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            Long folderId = importFolder.get().getId();

            File torrentContentFile = new File(qbTorrentInfo.getContentPath());
            importFileByHardLinkRecursively(torrentContentFile, folderId);

        }
        log.info("end import qbittorrent files that has download finished, size: {}.",
            downloadProcessFinishTorrentList.size());
    }

    private void importFileByHardLinkRecursively(File torrentContentFile, Long folderId) {
        Assert.notNull(torrentContentFile, "'torrentContentFile' must not null.");
        Assert.isTrue(folderId > 0, "'torrentContentFile' must not null.");
        if (torrentContentFile.isFile()) {
            String fileName = torrentContentFile.getName();
            String postfix = FileUtils.parseFilePostfix(fileName);
            FileType fileType = FileUtils.parseTypeByPostfix(postfix);
            AtomicReference<List<run.ikaros.api.core.file.File>> existsFiles =
                new AtomicReference<>();
            fileOperate.findAllByNameLikeAndType(fileName, fileType)
                .collectList()
                .subscribe(existsFiles::set);
            while (Objects.isNull(existsFiles.get())) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (existsFiles.get().size() > 0) {
                return;
            }

            String md5Hash = "";
            try {
                md5Hash =
                    FileUtils.calculateFileHash(
                        FileUtils.convertToDataBufferFlux(torrentContentFile));
            } catch (IOException e) {
                log.error("calculate file md5 fail.", e);
                return;
            }

            AtomicReference<Boolean> exists = new AtomicReference<>();
            fileOperate.existsByMd5(md5Hash)
                .subscribe(exists::set);
            while (Objects.isNull(exists.get())) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (Boolean.TRUE.equals(exists.get())) {
                return;
            }

            String importFilePath =
                FileUtils.buildAppUploadFilePath(ikarosProperties.getWorkDir().toString(), postfix);
            File importFile = new File(importFilePath);
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
                    throw new RuntimeException(ex);
                }
            }

            fileOperate.create(new run.ikaros.api.core.file.File()
                    .setName(fileName)
                    .setCanRead(true)
                    .setSize(torrentContentFile.length())
                    .setFsPath(importFilePath)
                    .setFolderId(folderId)
                    .setMd5(md5Hash)
                    .setUpdateTime(LocalDateTime.now())
                    .setType(fileType)
                    .setUrl(FileUtils.path2url(importFilePath, ikarosProperties.getWorkDir().toString())))
                .map(file -> {
                    log.info("import torrent file success for {}.",
                        torrentContentFile.getName());
                    return file;
                })
                .subscribe();
        } else {
            String name = torrentContentFile.getName();
            AtomicReference<Folder> folder = new AtomicReference<>();
            folderOperate.findByParentIdAndName(folderId, name)
                .switchIfEmpty(folderOperate.create(folderId, name))
                .subscribe(folder::set);
            while (Objects.isNull(folder.get())) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (folder.get() == null) {
                log.warn("folder is null for torrent folder: {}", torrentContentFile.getName());
                return;
            }
            File[] files = torrentContentFile.listFiles();
            if (Objects.isNull(files)) {
                return;
            }
            for (File file : files) {
                importFileByHardLinkRecursively(file, folder.get().getId());
            }
        }
    }

//    private String addPrefixForQbittorrentDownloadPath(String torrentContentPath) {
//        return ikarosProperties.getWorkDir().toString()
//            + (torrentContentPath.startsWith(String.valueOf(File.separatorChar))
//            ? torrentContentPath : (File.separatorChar + torrentContentPath));
//    }


}
