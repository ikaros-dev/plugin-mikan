package run.ikaros.plugin.mikan;


import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;
import run.ikaros.api.plugin.BasePlugin;

@Slf4j
@Component
public class MikanPlugin extends BasePlugin {
    public static final String NAME = "PluginMikan";

    public MikanPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("plugin [{}] start success", NAME);
    }

    @Override
    public void stop() {
        log.info("plugin [{}] stop success", NAME);
    }

    @Override
    public void delete() {
        log.info("plugin [{}] delete success", NAME);
    }
}