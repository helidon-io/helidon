/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.webserver.staticcontent;

import java.lang.System.Logger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weighted;
import io.helidon.common.media.type.MediaType;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.spi.ServerFeature;

/**
 * WebServer feature to register static content.
 */
@RuntimeType.PrototypedBy(StaticContentConfig.class)
public class StaticContentFeature implements Weighted, ServerFeature, RuntimeType.Api<StaticContentConfig> {
    static final String STATIC_CONTENT_ID = "static-content";
    static final double WEIGHT = 95;

    private static final Logger LOGGER = System.getLogger(StaticContentFeature.class.getName());

    private final StaticContentConfig config;
    private final MemoryCache memoryCache;
    private final TemporaryStorage temporaryStorage;
    private final Map<String, MediaType> contentTypeMapping;
    private final boolean enabled;
    private final Set<String> sockets;
    private final Optional<String> welcome;

    private StaticContentFeature(StaticContentConfig config) {
        this.config = config;
        this.enabled = config.enabled() && !(config.classpath().isEmpty() && config.path().isEmpty());
        if (enabled) {
            this.contentTypeMapping = config.contentTypes();
            this.memoryCache = config.memoryCache()
                        .orElseGet(MemoryCache::create);
            this.sockets = config.sockets();
            this.welcome = config.welcome();

            if (config.classpath().isEmpty()) {
                this.temporaryStorage = null;
            } else {
                this.temporaryStorage = config.temporaryStorage()
                        .orElseGet(TemporaryStorage::create);
            }
        } else {
            this.sockets = Set.of();
            this.welcome = Optional.empty();
            this.memoryCache = null;
            this.temporaryStorage = null;
            this.contentTypeMapping = null;
        }
    }

    /**
     * Create Access log support configured from {@link io.helidon.config.Config}.
     *
     * @param config to configure a new access log support instance
     * @return a new access log support to be registered with WebServer routing
     */
    public static StaticContentFeature create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * A new fluent API builder to create Access log support instance.
     *
     * @return a new builder
     */
    public static StaticContentConfig.Builder builder() {
        return StaticContentConfig.builder();
    }

    /**
     * Create a new instance from its configuration.
     *
     * @param config configuration
     * @return a new feature
     */
    public static StaticContentFeature create(StaticContentConfig config) {
        return new StaticContentFeature(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param builderConsumer consumer of configuration
     * @return a new feature
     */
    public static StaticContentFeature create(Consumer<StaticContentConfig.Builder> builderConsumer) {
        return builder()
                .update(builderConsumer)
                .build();
    }

    /**
     * Create an Http service for file system based content handler.
     *
     * @param config configuration of the content handler
     * @return a new HTTP service ready to be registered
     */
    public static HttpService createService(FileSystemHandlerConfig config) {
        return FileSystemContentHandler.create(config);
    }

    /**
     * Create an Http service for classpath based content handler.
     *
     * @param config configuration of the content handler
     * @return a new HTTP service ready to be registered
     */
    public static HttpService createService(ClasspathHandlerConfig config) {
        return ClassPathContentHandler.create(config);
    }

    @Override
    public StaticContentConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return STATIC_CONTENT_ID;
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        if (!enabled) {
            return;
        }

        Set<String> defaultSockets;
        if (this.sockets.isEmpty()) {
            defaultSockets = new HashSet<>(featureContext.sockets());
            defaultSockets.add(WebServer.DEFAULT_SOCKET_NAME);
        } else {
            defaultSockets = new HashSet<>(this.sockets);
        }

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        for (ClasspathHandlerConfig handlerConfig : config.classpath()) {
            if (!handlerConfig.enabled()) {
                continue;
            }


            Set<String> handlerSockets = handlerConfig.sockets().isEmpty()
                    ? defaultSockets
                    : handlerConfig.sockets();
            MemoryCache handlerCache = handlerConfig.memoryCache()
                    .orElse(this.memoryCache);
            TemporaryStorage handlerTmpStorage = handlerConfig.temporaryStorage()
                    .orElse(this.temporaryStorage);
            ClassLoader handlerClassLoader = handlerConfig.classLoader()
                    .orElse(contextClassLoader);
            Optional<String> welcome = handlerConfig.welcome().or(() -> this.welcome);
            Map<String, MediaType> contentTypeMap = new HashMap<>(this.contentTypeMapping);
            contentTypeMap.putAll(handlerConfig.contentTypes());

            for (String handlerSocket : handlerSockets) {
                if (!featureContext.socketExists(handlerSocket)) {
                    LOGGER.log(Logger.Level.WARNING, "Static content handler is configured for socket \"" + handlerSocket
                            + "\" that is not configured on the server");
                    continue;
                }

                handlerConfig = ClasspathHandlerConfig.builder()
                        .from(handlerConfig)
                        .memoryCache(handlerCache)
                        .temporaryStorage(handlerTmpStorage)
                        .update(it -> welcome.ifPresent(it::welcome))
                        .classLoader(handlerClassLoader)
                        .contentTypes(contentTypeMap)
                        .build();

                HttpService service = createService(handlerConfig);
                featureContext.socket(handlerSocket)
                        .httpRouting()
                        .register(handlerConfig.context(), service);
            }
        }
        for (FileSystemHandlerConfig handlerConfig : config.path()) {
            if (!handlerConfig.enabled()) {
                continue;
            }
            Set<String> handlerSockets = handlerConfig.sockets().isEmpty()
                    ? defaultSockets
                    : handlerConfig.sockets();
            MemoryCache handlerCache = handlerConfig.memoryCache()
                    .orElse(this.memoryCache);
            Map<String, MediaType> contentTypeMap = new HashMap<>(this.contentTypeMapping);
            contentTypeMap.putAll(handlerConfig.contentTypes());

            for (String handlerSocket : handlerSockets) {
                if (!featureContext.socketExists(handlerSocket)) {
                    LOGGER.log(Logger.Level.WARNING, "Static content handler is configured for socket \"" + handlerSocket
                            + "\" that is not configured on the server");
                    continue;
                }

                handlerConfig = FileSystemHandlerConfig.builder()
                        .from(handlerConfig)
                        .memoryCache(handlerCache)
                        .contentTypes(contentTypeMap)
                        .build();

                HttpService service = createService(handlerConfig);
                featureContext.socket(handlerSocket)
                        .httpRouting()
                        .register(handlerConfig.context(), service);
            }
        }
    }
}
