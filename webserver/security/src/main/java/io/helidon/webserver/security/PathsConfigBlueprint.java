package io.helidon.webserver.security;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.http.Method;
import io.helidon.webserver.WebServer;

@Prototype.Blueprint
@Prototype.Configured
interface PathsConfigBlueprint {
    @Prototype.FactoryMethod
    static Method createMethod(Config config) {
        return config.asString().map(Method::create).orElseThrow();
    }

    @Option.Configured
    @Option.Singular
    List<Method> methods();

    @Option.Configured
    String path();

    @Option.Configured
    @Option.Default(WebServer.DEFAULT_SOCKET_NAME)
    List<String> sockets();

    @Option.Configured(merge = true)
    SecurityHandlerConfig handler();
}
