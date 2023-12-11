/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.cors.CorsEnabledServiceHelper;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Helidon Support for OpenAPI.
 */
@RuntimeType.PrototypedBy(OpenApiFeatureConfig.class)
public final class OpenApiFeature implements ServerFeature, RuntimeType.Api<OpenApiFeatureConfig> {

    static final String OPENAPI_ID = "openapi";
    static final double WEIGHT = 90;
    static final Map<String, MediaType> SUPPORTED_FORMATS = Map.of(
            "json", MediaTypes.APPLICATION_JSON,
            "yaml", MediaTypes.APPLICATION_OPENAPI_YAML,
            "yml", MediaTypes.APPLICATION_OPENAPI_YAML);
    private static final System.Logger LOGGER = System.getLogger(OpenApiFeature.class.getName());
    private static final String DEFAULT_STATIC_FILE_PATH_PREFIX = "META-INF/openapi.";
    private static final List<String> DEFAULT_FILE_PATHS = SUPPORTED_FORMATS.keySet()
            .stream()
            .map(fileType -> DEFAULT_STATIC_FILE_PATH_PREFIX + fileType)
            .toList();
    private final String content;
    private final OpenApiFeatureConfig config;
    private final CorsEnabledServiceHelper corsService;
    private final OpenApiManager<?> manager;
    private final LazyValue<Object> model;

    OpenApiFeature(OpenApiFeatureConfig config) {
        this.config = config;
        String staticFile = config.staticFile().orElse(null);
        String defaultContent = null;
        if (staticFile != null) {
            defaultContent = readContent(staticFile);
            if (defaultContent == null) {
                defaultContent = "";
                LOGGER.log(Level.WARNING, "Static OpenAPI file not found: {0}", staticFile);
            }
        } else {
            for (String path : DEFAULT_FILE_PATHS) {
                defaultContent = readContent(path);
                if (defaultContent != null) {
                    break;
                }
            }
            if (defaultContent == null) {
                defaultContent = "";
                LOGGER.log(Level.WARNING, "Static OpenAPI file not found, checked: {0}", DEFAULT_FILE_PATHS);
            }
        }
        content = defaultContent;
        manager = config.manager().orElseGet(SimpleOpenApiManager::new);
        corsService = CorsEnabledServiceHelper.create("openapi", config.cors().orElse(null));
        model = LazyValue.create(() -> manager.load(content));
    }

    /**
     * Returns a new builder.
     *
     * @return new builder`
     */
    public static OpenApiFeatureConfig.Builder builder() {
        return OpenApiFeatureConfig.builder();
    }

    /**
     * Create a new instance with default configuration.
     *
     * @return new instance
     */
    public static OpenApiFeature create() {
        return builder().build();
    }

    /**
     * Create a new instance from typed configuration.
     *
     * @param config typed configuration
     * @return new instance
     */
    public static OpenApiFeature create(Config config) {
        return new OpenApiFeature(OpenApiFeatureConfig.create(config));
    }

    /**
     * Create a new instance with custom configuration.
     *
     * @param builderConsumer consumer of configuration builder
     * @return new instance
     */
    public static OpenApiFeature create(Consumer<OpenApiFeatureConfig.Builder> builderConsumer) {
        return OpenApiFeatureConfig.builder().update(builderConsumer).build();
    }

    /**
     * Create a new instance from typed configuration.
     *
     * @param config typed configuration
     * @return new instance
     */
    static OpenApiFeature create(OpenApiFeatureConfig config) {
        return new OpenApiFeature(config);
    }

    @Override
    public OpenApiFeatureConfig prototype() {
        return config;
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        if (!config.isEnabled()) {
            return;
        }

        Set<String> sockets = new HashSet<>(config.sockets());
        if (sockets.isEmpty()) {
            sockets.addAll(featureContext.sockets());
            sockets.add(WebServer.DEFAULT_SOCKET_NAME);
        }

        for (String socket : sockets) {
            featureContext.socket(socket)
                    .httpRouting()
                    .addFeature(new OpenApiHttpFeature(config, manager, model, corsService));
        }
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return OPENAPI_ID;
    }

    /**
     * Initialize the model.
     */
    public void initialize() {
        model.get();
    }

    private static String readContent(String path) {
        try {
            Path file = Path.of(path);
            if (Files.exists(file)) {
                return Files.readString(file);
            } else {
                try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
                    return is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : null;
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
