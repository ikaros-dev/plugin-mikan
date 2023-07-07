package run.ikaros.plugin.starter;

import lombok.extern.slf4j.Slf4j;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.fn.builders.apiresponse.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.ikaros.api.custom.GroupVersionKind;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.endpoint.CustomEndpoint;

@Slf4j
@Component
public class StarterEndpoint implements CustomEndpoint {

    private final ReactiveCustomClient reactiveCustomClient;
    private GroupVersionKind groupVersionKind =
        new GroupVersionKind("plugin.ikaros.run", "v1alpha1", "Stater");

    public StarterEndpoint(ReactiveCustomClient reactiveCustomClient) {
        this.reactiveCustomClient = reactiveCustomClient;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = groupVersionKind.group()
            + "/" + groupVersionKind.version()
            + "/" + groupVersionKind.kind();
        return SpringdocRouteBuilder.route()
            .GET("/findAll", this::findAll,
                builder -> builder
                    .operationId("findAllStarter")
                    .tag(tag)
                    .response(Builder.responseBuilder()
                        .implementationArray(StarterCustom.class)
                        .description("Start custom array.")))
            .GET("/findOne/{name}", this::findOne,
                builder -> builder
                    .operationId("findOneStarter")
                    .description("Find one starter by path name.")
                    .tag(tag)
                    .parameter(org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .implementation(String.class)
                        .description("Starter unit name."))
                    .response(Builder.responseBuilder()
                        .implementation(StarterCustom.class)
                        .description("Starter custom.")))
            .DELETE("/{name}", this::deleteByName,
                builder -> builder
                    .operationId("deleteByName")
                    .description("Delete one starter by path name.")
                    .tag(tag)
                    .parameter(org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .implementation(String.class)
                        .description("Starter unit name."))
                    .response(Builder.responseBuilder()
                        .description("is Success.")))
            .PUT("", this::save,
                builder -> builder
                    .operationId("save")
                    .description("Save a starter obj.")
                    .tag(tag)
                    .requestBody(
                        org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder()
                            .implementation(StarterCustom.class)
                            .description("StarterCustom")))
            .build();
    }

    Mono<ServerResponse> findAll(ServerRequest request) {
        return reactiveCustomClient.findAll(StarterCustom.class, null)
            .collectList()
            .flatMap(customs -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(customs));
    }

    Mono<ServerResponse> findOne(ServerRequest request) {
        String name = request.pathVariable("name");
        return reactiveCustomClient.findOne(StarterCustom.class, name)
            .flatMap(custom -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(custom));
    }

    Mono<ServerResponse> save(ServerRequest request) {
        return request.bodyToMono(StarterCustom.class)
            .flatMap(reactiveCustomClient::create)
            .flatMap(custom -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(custom));
    }

    Mono<ServerResponse> deleteByName(ServerRequest request) {
        String name = request.pathVariable("name");
        return reactiveCustomClient.delete(StarterCustom.class, name)
            .flatMap(custom -> ServerResponse.ok()
                .bodyValue("SUCCESS"));
    }

    @Override
    public GroupVersionKind groupVersionKind() {
        return groupVersionKind;
    }
}
