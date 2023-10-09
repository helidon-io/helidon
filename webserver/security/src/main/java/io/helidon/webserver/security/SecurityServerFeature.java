package io.helidon.webserver.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.config.Config;
import io.helidon.common.context.Contexts;
import io.helidon.security.Security;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Server feature for security, to be registered with
 * {@link io.helidon.webserver.WebServerConfig.Builder#addFeature}.
 */
public class SecurityServerFeature implements ServerFeature {
    static final String SECURITY_TYPE = "security";

    private final Security security;
    private final String name;
    private final SecurityServerFeatureConfig featureConfig;

    public SecurityServerFeature(Security security, String name, SecurityServerFeatureConfig featureConfig) {
        this.security = security;
        this.name = name;
        this.featureConfig = featureConfig;
    }

    /**
     * Create a security feature from configuration. {@link io.helidon.security.Security} instance is obtained either
     * from {@link io.helidon.common.context.Context}, or created from configuration.
     *
     * @param config configuration for the security feature
     * @param name   name of this feature instance
     * @return a new instance
     */
    public static SecurityServerFeature create(Config config, String name) {
        Security security = Contexts.globalContext().get(Security.class)
                .orElseGet(() -> Security.create(config.root().get("security")));

        return new SecurityServerFeature(security,
                                         name,
                                         SecurityServerFeatureConfig.create(config));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return SECURITY_TYPE;
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        SecurityHandlerConfig defaults = featureConfig.defaults();
        List<PathsConfig> paths = featureConfig.paths();

        Map<String, List<SecurityHandlerConfig>> configurations = new HashMap<>();
        for (PathsConfig path : paths) {
            List<String> sockets = new ArrayList<>(path.sockets());
            SecurityHandlerConfig handler = path.handler();
            if (sockets.isEmpty()) {
                sockets.add(WebServer.DEFAULT_SOCKET_NAME);
            }
            for (String socket : sockets) {
                configurations.computeIfAbsent(socket, it -> new ArrayList<>())
                        .add(handler);
            }
        }

        configurations.forEach((socketName, configs) -> {
            if (featureContext.socketExists(socketName)) {
                SocketBuilders socket = featureContext.socket(socketName);
                // socket.httpRouting().addFeature(SecurityFeature.create(security, defaults, configs));
            }
        });
    }
}
