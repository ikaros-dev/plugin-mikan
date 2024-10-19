package run.ikaros.plugin.mikan;


import lombok.extern.slf4j.Slf4j;

import org.pf4j.PluginWrapper;
import org.pf4j.RuntimeMode;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import run.ikaros.api.plugin.BasePlugin;

import java.util.Objects;

@Slf4j
@Component
public class MikanPlugin extends BasePlugin {
    public static final String NAME = "PluginMikan";
    private final MikanSubHandler mikanSubHandler;

    private Disposable parseMikanSubRssAndAddToQbittorrentDisposable;
    private Disposable importQbittorrentFilesAndAddSubjectDisposable;

    public MikanPlugin(PluginWrapper wrapper, MikanSubHandler mikanSubHandler) {
        super(wrapper);
        this.mikanSubHandler = mikanSubHandler;
    }

    @Override
    public void start() {
        RuntimeMode pluginRuntimeMode = getWrapper().getRuntimeMode();
        mikanSubHandler.setPluginRuntimeMode(pluginRuntimeMode);
        mikanSubHandler.init();
        parseMikanSubRssAndAddToQbittorrentDisposable =
            mikanSubHandler.startParseMikanSubRssAndAddToQbittorrent();
        importQbittorrentFilesAndAddSubjectDisposable =
            mikanSubHandler.startImportQbittorrentFilesAndAddSubject();
        log.info("plugin [{}] start success", NAME);
    }

    @Override
    public void stop() {
        if (Objects.nonNull(parseMikanSubRssAndAddToQbittorrentDisposable)) {
            parseMikanSubRssAndAddToQbittorrentDisposable.dispose();
        }
        if (Objects.nonNull(importQbittorrentFilesAndAddSubjectDisposable)) {
            importQbittorrentFilesAndAddSubjectDisposable.dispose();
        }
        log.info("plugin [{}] stop success", NAME);
    }

    @Override
    public void delete() {
        // log.info("plugin [{}] delete success", NAME);
    }
}