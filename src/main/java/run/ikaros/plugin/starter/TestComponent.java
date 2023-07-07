package run.ikaros.plugin.starter;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.exception.NotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TestComponent {
    private final ReactiveCustomClient reactiveCustomClient;
    private StarterCustom starterCustom;

    public TestComponent(ReactiveCustomClient reactiveCustomClient) {
        this.reactiveCustomClient = reactiveCustomClient;
    }


    @EventListener(ApplicationReadyEvent.class)
    public Mono<Void> afterPropertiesSet() throws Exception {
        starterCustom = StarterCustom
            .builder()
            .title("starter")
            .build();

        return reactiveCustomClient
            .findOne(StarterCustom.class, starterCustom.getTitle())
            .onErrorResume(NotFoundException.class, e -> reactiveCustomClient.create(starterCustom)
                .doOnSuccess(sc -> log.info("create starter custom: {}", starterCustom)))
            .doOnSuccess(sc -> log.info("starter custom exists: {}", sc))
            .then();
    }
}
