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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.BadRequestException;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;
import io.helidon.webserver.cors.CorsEnabledServiceHelper;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.SecureHandler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.servicecommon.FeatureSupport;

/**
 * Helidon Support for OpenAPI.
 */
@RuntimeType.PrototypedBy(OpenApiFeatureConfig.class)
public final class OpenApiFeature implements FeatureSupport, RuntimeType.Api<OpenApiFeatureConfig> {

    private static final System.Logger LOGGER = System.getLogger(OpenApiFeature.class.getName());

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
     * Create a new instance from typed configuration.
     *
     * @param config typed configuration
     * @return new instance
     */
    static OpenApiFeature create(OpenApiFeatureConfig config) {
        return new OpenApiFeature(config);
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

    private static final String DEFAULT_STATIC_FILE_PATH_PREFIX = "META-INF/openapi.";
    private static final Map<String, MediaType> SUPPORTED_FORMATS = Map.of(
            "json", MediaTypes.APPLICATION_JSON,
            "yaml", MediaTypes.APPLICATION_OPENAPI_YAML,
            "yml", MediaTypes.APPLICATION_OPENAPI_YAML);
    private static final List<String> DEFAULT_FILE_PATHS = SUPPORTED_FORMATS.keySet()
            .stream()
            .map(fileType -> DEFAULT_STATIC_FILE_PATH_PREFIX + fileType)
            .toList();
    private static final MediaType[] PREFERRED_MEDIA_TYPES = new MediaType[] {
            MediaTypes.APPLICATION_OPENAPI_YAML,
            MediaTypes.APPLICATION_X_YAML,
            MediaTypes.APPLICATION_YAML,
            MediaTypes.APPLICATION_OPENAPI_JSON,
            MediaTypes.APPLICATION_JSON,
            MediaTypes.TEXT_X_YAML,
            MediaTypes.TEXT_YAML
    };

    private final String content;
    private final OpenApiFeatureConfig config;
    private final CorsEnabledServiceHelper corsService;
    private final OpenApiManager<?> manager;
    private final LazyValue<Object> model;
    private final ConcurrentMap<OpenApiFormat, String> cachedDocuments = new ConcurrentHashMap<>();

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

    @Override
    public OpenApiFeatureConfig prototype() {
        return config;
    }

    @Override
    public void setup(HttpRouting.Builder routing, HttpRouting.Builder featureRouting) {
        String path = prototype().webContext();
        if (!config.permitAll()) {
            routing.any(path, SecureHandler.authorize(config.roles().toArray(new String[0])));
        }
        routing.any(path, corsService.processor())
                .get(path, this::handle);
        config.services().forEach(service -> service.setup(routing, path, this::content));
    }

    @Override
    public String context() {
        return config.webContext();
    }

    @Override
    public String configuredContext() {
        return config.webContext();
    }

    @Override
    public boolean enabled() {
        return config.isEnabled();
    }

    /**
     * Initialize the model.
     */
    public void initialize() {
        model.get();
    }

    private void handle(ServerRequest req, ServerResponse res) {
        String format = req.query().first("format").map(String::toLowerCase).orElse(null);
        if (format != null) {
            MediaType contentType = SUPPORTED_FORMATS.get(format.toLowerCase());
            if (contentType == null) {
                throw new BadRequestException(String.format(
                        "Unsupported format: %s, supported formats: %s",
                        format, SUPPORTED_FORMATS.keySet()));
            }
            res.status(Status.OK_200);
            res.headers().contentType(contentType);
            res.send(content(contentType));
        } else {
            // check if we should delegate to a service
            for (OpenApiService service : config.services()) {
                if (service.supports(req.headers())) {
                    res.next();
                    return;
                }
            }

            HttpMediaType contentType = req.headers()
                    .bestAccepted(PREFERRED_MEDIA_TYPES)
                    .map(HttpMediaType::create)
                    .orElse(null);

            if (contentType == null) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Accepted types not supported: {0}", req.headers().acceptedTypes());
                }
                res.next();
                return;
            }

            res.status(Status.OK_200);
            res.headers().contentType(contentType);
            res.send(content(contentType));
        }
    }

    private String content(MediaType mediaType) {
        OpenApiFormat format = OpenApiFormat.valueOf(mediaType);
        if (format == OpenApiFormat.UNSUPPORTED) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Requested format {0} not supported", mediaType);
            }
        }
        return cachedDocuments.computeIfAbsent(format, fmt -> format(manager, fmt, model.get()));
    }

    @SuppressWarnings("unchecked")
    private static <T> String format(OpenApiManager<T> manager, OpenApiFormat format, Object model) {
        return manager.format((T) model, format);
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
