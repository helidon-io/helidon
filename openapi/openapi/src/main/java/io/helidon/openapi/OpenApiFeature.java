/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.openapi.spi.OpenApiDocumentSource;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.spi.OpenApiVersionProvider;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.ServerFeature;

import org.yaml.snakeyaml.Yaml;

/**
 * Helidon Support for OpenAPI.
 */
@Weight(OpenApiFeature.WEIGHT)
@Service.Singleton
public final class OpenApiFeature implements Weighted, ServerFeature, RuntimeType.Api<OpenApiFeatureConfig> {

    static final String OPENAPI_ID = "openapi";
    static final double WEIGHT = 90;
    static final Map<String, MediaType> SUPPORTED_FORMATS = Map.of(
            "json", MediaTypes.APPLICATION_JSON,
            "yaml", MediaTypes.APPLICATION_OPENAPI_YAML,
            "yml", MediaTypes.APPLICATION_OPENAPI_YAML);
    private static final System.Logger LOGGER = System.getLogger(OpenApiFeature.class.getName());
    private static final String STANDBY_NAME = OPENAPI_ID + "-service-registry";
    private static final String DEFAULT_STATIC_FILE_PATH_PREFIX = "META-INF/openapi.";
    private static final List<String> DEFAULT_FILE_PATHS = SUPPORTED_FORMATS.keySet()
            .stream()
            .map(fileType -> DEFAULT_STATIC_FILE_PATH_PREFIX + fileType)
            .toList();
    private final String content;
    private final MediaType contentMediaType;
    private final OpenApiFormat contentFormat;
    private final Optional<String> contentOpenApiVersion;
    private final OpenApiFeatureConfig config;
    private final OpenApiManager<?> manager;
    private final LazyValue<Object> model;
    private final LazyValue<List<OpenApiDocumentSource>> documentSources;
    private final LazyValue<List<OpenApiVersion>> openApiVersions;

    OpenApiFeature(OpenApiFeatureConfig config) {
        this(config, List::of, OpenApiFeature::openApiVersionProviders);
    }

    @Service.Inject
    OpenApiFeature(ServiceRegistry registry,
                   Config config,
                   Supplier<List<OpenApiDocumentSource>> documentSources,
                   Supplier<List<OpenApiVersionProvider>> openApiVersionProviders) {
        this(serviceConfig(registry, config), documentSources, openApiVersionProviders);
    }

    OpenApiFeature(ServiceRegistry registry, Config config, Supplier<List<OpenApiDocumentSource>> documentSources) {
        this(serviceConfig(registry, config), documentSources, OpenApiFeature::openApiVersionProviders);
    }

    OpenApiFeature(Config config, Supplier<List<OpenApiDocumentSource>> documentSources) {
        this(serviceConfig(config), documentSources, OpenApiFeature::openApiVersionProviders);
    }

    OpenApiFeature(OpenApiFeatureConfig config,
                   Supplier<List<OpenApiDocumentSource>> documentSources,
                   Supplier<List<OpenApiVersionProvider>> openApiVersionProviders) {
        this.config = config;
        this.documentSources = LazyValue.create(documentSources);
        this.openApiVersions = LazyValue.create(() -> openApiVersions(openApiVersionProviders.get()));
        String staticFile = config.staticFile().orElse(null);
        String defaultContent = null;
        MediaType defaultContentMediaType = MediaTypes.APPLICATION_OCTET_STREAM;
        OpenApiFormat defaultContentFormat = OpenApiFormat.UNSUPPORTED;
        if (staticFile != null) {
            defaultContent = readContent(staticFile);
            if (defaultContent == null) {
                defaultContent = "";
                LOGGER.log(Level.WARNING, "Static OpenAPI file not found: {0}", staticFile);
            } else {
                defaultContentMediaType = contentTypeOf(staticFile);
                defaultContentFormat = OpenApiFormat.valueOf(defaultContentMediaType);
            }
        } else {
            for (String path : DEFAULT_FILE_PATHS) {
                defaultContent = readContent(path);
                if (defaultContent != null) {
                    defaultContentMediaType = contentTypeOf(path);
                    defaultContentFormat = OpenApiFormat.valueOf(defaultContentMediaType);
                    break;
                }
            }
            if (defaultContent == null) {
                defaultContent = "";
                LOGGER.log(Level.DEBUG, "Static OpenAPI file not found, checked: {0}", DEFAULT_FILE_PATHS);
            }
        }
        content = defaultContent;
        contentMediaType = defaultContentMediaType;
        contentFormat = defaultContentFormat;
        contentOpenApiVersion = openApiVersion(defaultContent);
        manager = config.manager().orElseGet(SimpleOpenApiManager::new);
        model = LazyValue.create(() -> manager.load(documentContent(WebServer.DEFAULT_SOCKET_NAME, documentSources())));
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
            LazyValue<Object> socketModel = LazyValue.create(() -> manager.load(documentContent(socket, documentSources())));
            featureContext.socket(socket)
                    .httpRouting()
                    .addFeature(new OpenApiHttpFeature(config,
                                                       manager,
                                                       socketModel,
                                                       exactStaticContent(),
                                                       contentFormat));
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

    @Override
    public double weight() {
        return config.weight();
    }

    /**
     * Initialize the model.
     */
    public void initialize() {
        model.get();
    }

    private String documentContent(String listener, List<OpenApiDocumentSource> sources) {
        boolean hasStaticContent = !content.isBlank();
        OpenApiGeneratedMode mode = config.generatedMode();
        if (mode == OpenApiGeneratedMode.STATIC_ONLY
                || (hasStaticContent && mode == OpenApiGeneratedMode.STATIC_FIRST)) {
            return finalStaticContent(listener);
        }
        if (sources.isEmpty()) {
            if (mode == OpenApiGeneratedMode.GENERATED_ONLY) {
                return "";
            }
            return hasStaticContent ? finalStaticContent(listener) : "";
        }

        OpenApiVersion openApiVersion = config.openApiVersion()
                .orElseThrow(() -> new IllegalStateException("No OpenAPI version provider is available."));
        OpenApiDocumentContext context = new OpenApiDocumentContext(config.name(),
                                                                    config.webContext(),
                                                                    listener,
                                                                    mode,
                                                                    openApiVersion);
        OpenApiVersion staticVersion = hasStaticContent ? staticOpenApiVersion() : openApiVersion;
        return OpenApiDocumentComposer.compose(context, staticVersion, content, contentMediaType, sources);
    }

    private String finalStaticContent(String listener) {
        if (content.isBlank()) {
            return content;
        }
        Optional<OpenApiVersion> openApiVersion = config.openApiVersion();
        if (openApiVersion.isEmpty()
                || contentOpenApiVersion.filter(it -> compatibleOpenApiVersion(openApiVersion.get(), it)).isPresent()) {
            return content;
        }
        OpenApiDocumentContext context = new OpenApiDocumentContext(config.name(),
                                                                    config.webContext(),
                                                                    listener,
                                                                    config.generatedMode(),
                                                                    openApiVersion.get());
        OpenApiVersion staticOpenApiVersion = staticOpenApiVersion();
        OpenApiDocumentContext staticContext = new OpenApiDocumentContext(config.name(),
                                                                          config.webContext(),
                                                                          listener,
                                                                          config.generatedMode(),
                                                                          staticOpenApiVersion);
        return openApiVersion.get().render(context, staticOpenApiVersion.parse(staticContext, content, contentMediaType));
    }

    private List<OpenApiDocumentSource> documentSources() {
        if (!shouldLookupDocumentSources()) {
            return List.of();
        }
        return documentSources.get();
    }

    private boolean shouldLookupDocumentSources() {
        return switch (config.generatedMode()) {
            case STATIC_FIRST -> content.isBlank();
            case STATIC_ONLY -> false;
            case MERGE, GENERATED_ONLY -> true;
        };
    }

    private Optional<String> exactStaticContent() {
        if (content.isBlank() || contentFormat == OpenApiFormat.UNSUPPORTED) {
            return Optional.empty();
        }
        Optional<OpenApiVersion> openApiVersion = config.openApiVersion();
        if (openApiVersion.isPresent()
                && contentOpenApiVersion.filter(it -> compatibleOpenApiVersion(openApiVersion.get(), it)).isEmpty()) {
            return Optional.empty();
        }
        return switch (config.generatedMode()) {
            case STATIC_ONLY, STATIC_FIRST -> Optional.of(content);
            case MERGE, GENERATED_ONLY -> Optional.empty();
        };
    }

    private static OpenApiFeatureConfig serviceConfig(ServiceRegistry registry, Config config) {
        return serviceConfig(() -> OpenApiFeatureConfig.builder().serviceRegistry(registry), config);
    }

    private static OpenApiFeatureConfig serviceConfig(Config config) {
        return serviceConfig(OpenApiFeatureConfig::builder, config);
    }

    private static OpenApiFeatureConfig serviceConfig(Supplier<OpenApiFeatureConfig.Builder> builderSupplier, Config config) {
        Config featuresConfig = config.get("server.features");
        Config openApiConfig = featuresConfig.get(OPENAPI_ID);
        if (openApiConfig.exists()) {
            return builderSupplier.get()
                    .config(openApiConfig)
                    .buildPrototype();
        }
        if (hasConfiguredOpenApiFeature(featuresConfig)) {
            return builderSupplier.get()
                    .isEnabled(false)
                    .name(STANDBY_NAME)
                    .buildPrototype();
        }
        return builderSupplier.get()
                .config(config.root().get(OPENAPI_ID))
                .buildPrototype();
    }

    private static boolean hasConfiguredOpenApiFeature(Config featuresConfig) {
        boolean featuresList = featuresConfig.isList();
        return featuresConfig.asNodeList()
                .orElseGet(List::of)
                .stream()
                .anyMatch(it -> isOpenApiFeatureConfig(it, featuresList));
    }

    private static boolean isOpenApiFeatureConfig(Config featureConfig, boolean listItem) {
        if (isOpenApiFeatureNode(featureConfig)) {
            return true;
        }
        if (!listItem) {
            return false;
        }
        return featureConfig.asNodeList()
                .orElseGet(List::of)
                .stream()
                .anyMatch(OpenApiFeature::isOpenApiFeatureNode);
    }

    private static boolean isOpenApiFeatureNode(Config featureConfig) {
        return OPENAPI_ID.equals(featureConfig.name())
                || OPENAPI_ID.equals(featureConfig.get("type").asString().orElse(null));
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

    private static MediaType contentTypeOf(String path) {
        return MediaTypes.detectType(path)
                .orElse(MediaTypes.APPLICATION_OCTET_STREAM);
    }

    private static List<OpenApiVersionProvider> openApiVersionProviders() {
        return HelidonServiceLoader.create(ServiceLoader.load(OpenApiVersionProvider.class))
                .asList();
    }

    private static List<OpenApiVersion> openApiVersions(List<OpenApiVersionProvider> providers) {
        return providers.stream()
                .map(provider -> provider.create(Config.empty(), provider.configKey()))
                .toList();
    }

    private static Optional<String> openApiVersion(String content) {
        if (content.isBlank()) {
            return Optional.empty();
        }
        try {
            Object loaded = new Yaml().load(content);
            if (loaded instanceof Map<?, ?> map) {
                return Optional.ofNullable(map.get("openapi")).map(String::valueOf);
            }
            return Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean compatibleOpenApiVersion(OpenApiVersion version, String openApiVersion) {
        String versionFamily = version.type();
        return openApiVersion.equals(version.version())
                || openApiVersion.equals(versionFamily)
                || openApiVersion.startsWith(versionFamily + ".");
    }

    private OpenApiVersion staticOpenApiVersion() {
        String staticDocumentVersion = contentOpenApiVersion.orElseThrow(() ->
                new IllegalStateException("Static OpenAPI document does not declare an openapi version."));
        Optional<OpenApiVersion> configuredVersion = config.openApiVersion()
                .filter(version -> compatibleOpenApiVersion(version, staticDocumentVersion));
        if (configuredVersion.isPresent()) {
            return configuredVersion.get();
        }
        return openApiVersions.get()
                .stream()
                .filter(version -> compatibleOpenApiVersion(version, staticDocumentVersion))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No OpenAPI version provider is available to parse static OpenAPI document version "
                                + staticDocumentVersion + "."));
    }
}
