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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.openapi.spi.OpenApiDocumentSource;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.spi.OpenApiVersionProvider;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;
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
    private static final String GENERATED_DOCUMENT_SOURCES_CONFIG_KEY = "generated.document-sources";
    private static final TypeName OPENAPI_DOCUMENT_SOURCE = TypeName.create(OpenApiDocumentSource.class);
    // Document sources and managers may be registry singletons shared by multiple feature instances.
    private static final ReentrantLock MODEL_LOAD_LOCK = new ReentrantLock();
    private static final List<String> DEFAULT_FILE_PATHS = SUPPORTED_FORMATS.keySet()
            .stream()
            .map(fileType -> DEFAULT_STATIC_FILE_PATH_PREFIX + fileType)
            .toList();
    private final String content;
    private final MediaType contentMediaType;
    private final OpenApiFormat contentFormat;
    private final ConcurrentMap<String, LazyValue<Optional<StaticOpenApiDocument>>> staticOpenApiDocuments;
    private final OpenApiFeatureConfig config;
    private final Config sourceConfig;
    private final OpenApiManager<?> manager;
    private final ConcurrentMap<String, LazyValue<Object>> modelsByListener;
    private final LazyValue<List<OpenApiDocumentSource>> documentSources;
    private final LazyValue<List<OpenApiVersion>> openApiVersions;
    private final AtomicBoolean initialized;
    private volatile List<LazyValue<Object>> listenerModels = List.of();

    OpenApiFeature(OpenApiFeatureConfig config) {
        this(GlobalServiceRegistry.registry(), Config.empty(), config);
    }

    @Service.Inject
    OpenApiFeature(ServiceRegistry registry,
                   Config config,
                   Supplier<List<OpenApiVersionProvider>> openApiVersionProviders) {
        this(registry, config.root(), serviceConfig(registry, config), openApiVersionProviders);
    }

    OpenApiFeature(ServiceRegistry registry, Config config) {
        this(registry, config.root(), serviceConfig(registry, config));
    }

    OpenApiFeature(ServiceRegistry registry, OpenApiFeatureConfig config) {
        this(registry, Config.empty(), config);
    }

    OpenApiFeature(Config config, Supplier<List<OpenApiDocumentSource>> documentSources) {
        this(config.root(), serviceConfig(config), documentSources, OpenApiFeature::openApiVersionProviders);
    }

    private OpenApiFeature(ServiceRegistry registry,
                           Config sourceConfig,
                           OpenApiFeatureConfig config,
                           Supplier<List<OpenApiVersionProvider>> openApiVersionProviders) {
        this(sourceConfig, config, documentSources(registry, config), openApiVersionProviders);
    }

    OpenApiFeature(ServiceRegistry registry, Config sourceConfig, OpenApiFeatureConfig config) {
        this(registry, sourceConfig, config, () -> registry.all(OpenApiVersionProvider.class));
    }

    OpenApiFeature(OpenApiFeatureConfig config,
                   Supplier<List<OpenApiDocumentSource>> documentSources,
                   Supplier<List<OpenApiVersionProvider>> openApiVersionProviders) {
        this(Config.empty(), config, documentSources, openApiVersionProviders);
    }

    OpenApiFeature(Config sourceConfig,
                   OpenApiFeatureConfig config,
                   Supplier<List<OpenApiDocumentSource>> documentSources,
                   Supplier<List<OpenApiVersionProvider>> openApiVersionProviders) {
        this.config = config;
        this.sourceConfig = sourceConfig;
        this.documentSources = LazyValue.create(documentSources);
        this.openApiVersions = LazyValue.create(() -> openApiVersions(openApiVersionProviders.get()));
        String defaultContent = "";
        MediaType defaultContentMediaType = MediaTypes.APPLICATION_OCTET_STREAM;
        OpenApiFormat defaultContentFormat = OpenApiFormat.UNSUPPORTED;
        if (config.isEnabled()) {
            String staticFile = config.staticFile().orElse(null);
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
        }
        content = defaultContent;
        contentMediaType = defaultContentMediaType;
        contentFormat = defaultContentFormat;
        staticOpenApiDocuments = new ConcurrentHashMap<>();
        manager = config.manager().orElseGet(SimpleOpenApiManager::new);
        modelsByListener = new ConcurrentHashMap<>();
        initialized = new AtomicBoolean();
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
        Config rootConfig = config.root();
        OpenApiFeatureConfig featureConfig = configureFeatureBuilder(OpenApiFeature.builder(), config)
                .buildPrototype();
        return new OpenApiFeature(GlobalServiceRegistry.registry(), rootConfig, featureConfig);
    }

    /**
     * Create a new instance with custom configuration.
     *
     * @param builderConsumer consumer of configuration builder
     * @return new instance
     */
    public static OpenApiFeature create(Consumer<OpenApiFeatureConfig.Builder> builderConsumer) {
        OpenApiFeatureConfig.Builder builder = builder();
        builderConsumer.accept(builder);
        return builder.build();
    }

    /**
     * Create a new instance from typed configuration.
     *
     * @param config typed configuration
     * @return new instance
     */
    static OpenApiFeature create(OpenApiFeatureConfig config) {
        return OpenApiFeatureConfigSupport.create(config);
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

        List<LazyValue<Object>> socketModels = new ArrayList<>(sockets.size());
        for (String socket : sockets) {
            LazyValue<Object> socketModel = listenerModel(socket);
            socketModels.add(socketModel);
            featureContext.socket(socket)
                    .httpRouting()
                    .addFeature(new OpenApiHttpFeature(config,
                                                       manager,
                                                       socketModel,
                                                       exactStaticContent(),
                                                       contentFormat));
        }
        listenerModels = List.copyOf(socketModels);
        if (initialized.get()) {
            listenerModels.forEach(LazyValue::get);
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
        if (!config.isEnabled()) {
            return;
        }
        initialized.set(true);
        List<LazyValue<Object>> currentListenerModels = listenerModels;
        if (currentListenerModels.isEmpty()) {
            listenerModel(WebServer.DEFAULT_SOCKET_NAME).get();
        } else {
            currentListenerModels.forEach(LazyValue::get);
        }
    }

    // Used by tests to verify static document version parsing stays lazy.
    boolean contentOpenApiVersionLoaded() {
        return staticOpenApiDocuments.values()
                .stream()
                .anyMatch(LazyValue::isLoaded);
    }

    private LazyValue<Object> model(String listener) {
        return LazyValue.create(() -> {
            MODEL_LOAD_LOCK.lock();
            try {
                return manager.load(documentContent(listener, documentSources()));
            } finally {
                MODEL_LOAD_LOCK.unlock();
            }
        });
    }

    private LazyValue<Object> listenerModel(String listener) {
        return modelsByListener.computeIfAbsent(listener, this::model);
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
        OpenApiDocumentContext context = new OpenApiDocumentContextImpl(config.name(),
                                                                        config.webContext(),
                                                                        listener,
                                                                        mode,
                                                                        openApiVersion,
                                                                        sourceConfig,
                                                                        config.generatedOperationIds(),
                                                                        config.generatedResolveConfigExpressions());
        Optional<Supplier<OpenApiDocument>> staticDocument = Optional.empty();
        if (mode == OpenApiGeneratedMode.MERGE && hasStaticContent) {
            staticDocument = Optional.of(() -> staticOpenApiDocument(listener).document());
        }
        return OpenApiDocumentComposer.compose(context, staticDocument, content, sources);
    }

    private String finalStaticContent(String listener) {
        if (content.isBlank()) {
            return content;
        }
        if (config.generatedMode() == OpenApiGeneratedMode.STATIC_ONLY
                || config.generatedMode() == OpenApiGeneratedMode.STATIC_FIRST) {
            return content;
        }
        Optional<OpenApiVersion> openApiVersion = config.openApiVersion();
        StaticOpenApiDocument staticDocument = staticOpenApiDocument(listener);
        if (openApiVersion.isEmpty()
                || compatibleOpenApiVersion(openApiVersion.get(), staticDocument.openApiVersion())) {
            return content;
        }
        OpenApiDocumentContext context = new OpenApiDocumentContextImpl(config.name(),
                                                                        config.webContext(),
                                                                        listener,
                                                                        config.generatedMode(),
                                                                        openApiVersion.get(),
                                                                        sourceConfig,
                                                                        config.generatedOperationIds(),
                                                                        config.generatedResolveConfigExpressions());
        return openApiVersion.get().render(context, staticDocument.document());
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
            return configureFeatureBuilder(builderSupplier.get(), openApiConfig)
                    .buildPrototype();
        }
        if (hasConfiguredOpenApiFeature(featuresConfig)) {
            OpenApiFeatureConfig.Builder builder = builderSupplier.get()
                    .isEnabled(false)
                    .name(STANDBY_NAME);
            disableProviderDiscovery(builder);
            return builder.buildPrototype();
        }
        return configureFeatureBuilder(builderSupplier.get(), config.root().get(OPENAPI_ID))
                .buildPrototype();
    }

    static OpenApiFeatureConfig.Builder configureFeatureBuilder(OpenApiFeatureConfig.Builder builder, Config config) {
        builder.config(config);
        if (!config.get("enabled").asBoolean().orElse(true)) {
            disableProviderDiscovery(builder);
        }
        return builder;
    }

    static void disableProviderDiscovery(OpenApiFeatureConfig.BuilderBase<?, ?> builder) {
        builder.openApiVersionDiscoverServices(false)
                .servicesDiscoverServices(false)
                .managerDiscoverServices(false);
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

    private static Supplier<List<OpenApiDocumentSource>> documentSources(ServiceRegistry registry,
                                                                         OpenApiFeatureConfig config) {
        return () -> documentSources(registry, config.generatedDocumentSources());
    }

    private static List<OpenApiDocumentSource> documentSources(ServiceRegistry registry, List<String> configuredNames) {
        List<ServiceInfo> services = registry.allServices(OPENAPI_DOCUMENT_SOURCE);
        List<ServiceInfo> unqualified = new ArrayList<>();
        Map<String, List<ServiceInfo>> named = new LinkedHashMap<>();

        for (ServiceInfo serviceInfo : services) {
            Optional<String> name = documentSourceName(serviceInfo);
            if (name.isPresent()) {
                named.computeIfAbsent(name.get(), ignored -> new ArrayList<>()).add(serviceInfo);
            } else {
                unqualified.add(serviceInfo);
            }
        }

        List<ServiceInfo> selected = new ArrayList<>();
        if (configuredNames.isEmpty()) {
            selectSingleNamedSource(named).ifPresent(selected::add);
        } else {
            for (String configuredName : configuredNames) {
                selected.add(selectNamedSource(named, configuredName));
            }
        }
        selected.addAll(unqualified);

        return selected.stream()
                .map(serviceInfo -> documentSource(registry, serviceInfo))
                .toList();
    }

    private static Optional<ServiceInfo> selectSingleNamedSource(Map<String, List<ServiceInfo>> named) {
        int namedCount = named.values()
                .stream()
                .mapToInt(List::size)
                .sum();
        if (namedCount == 0) {
            return Optional.empty();
        }
        if (namedCount == 1) {
            return Optional.of(named.values().iterator().next().getFirst());
        }

        throw new IllegalStateException("Multiple named OpenAPI document sources are available: "
                                                + namedDocumentSources(named)
                                                + ". Configure " + GENERATED_DOCUMENT_SOURCES_CONFIG_KEY
                                                + " for this OpenAPI feature with the source name to use.");
    }

    private static ServiceInfo selectNamedSource(Map<String, List<ServiceInfo>> named, String configuredName) {
        List<ServiceInfo> sources = named.get(configuredName);
        if (sources == null || sources.isEmpty()) {
            throw new IllegalStateException("Configured OpenAPI document source " + configuredName
                                                    + " was not found. Available named sources: " + named.keySet());
        }
        if (sources.size() > 1) {
            throw new IllegalStateException("Configured OpenAPI document source " + configuredName
                                                    + " matches multiple sources: " + serviceTypes(sources)
                                                    + ". Use unique source names or remove duplicate document metadata"
                                                    + " sources.");
        }
        return sources.getFirst();
    }

    private static Optional<String> documentSourceName(ServiceInfo serviceInfo) {
        return serviceInfo.qualifiers()
                .stream()
                .filter(qualifier -> Service.Named.TYPE.equals(qualifier.typeName()))
                .map(Qualifier::value)
                .flatMap(Optional::stream)
                .filter(name -> !name.isBlank())
                .filter(name -> !Service.Named.WILDCARD_NAME.equals(name))
                .findFirst();
    }

    private static OpenApiDocumentSource documentSource(ServiceRegistry registry, ServiceInfo serviceInfo) {
        return registry.<OpenApiDocumentSource>get(serviceInfo)
                .orElseThrow(() -> new IllegalStateException("OpenAPI document source "
                                                                     + serviceInfo.serviceType().fqName()
                                                                     + " is not available."));
    }

    private static String namedDocumentSources(Map<String, List<ServiceInfo>> named) {
        List<String> values = new ArrayList<>();
        named.forEach((name, sources) -> values.add(name + "=" + serviceTypes(sources)));
        return values.toString();
    }

    private static List<String> serviceTypes(List<ServiceInfo> sources) {
        return sources.stream()
                .map(serviceInfo -> serviceInfo.serviceType().fqName())
                .sorted()
                .toList();
    }

    private static List<OpenApiVersion> openApiVersions(List<OpenApiVersionProvider> providers) {
        return providers.stream()
                .map(provider -> provider.create(Config.empty(), provider.configKey()))
                .toList();
    }

    private Optional<StaticOpenApiDocument> parseStaticOpenApiDocument(String listener) {
        if (content.isBlank()) {
            return Optional.empty();
        }
        String openApiVersion = openApiVersion(content, contentFormat).orElseThrow(() ->
                new IllegalStateException("Static OpenAPI document does not declare an openapi version."));
        OpenApiVersion staticOpenApiVersion = staticOpenApiVersion(openApiVersion);
        OpenApiDocumentContext staticContext = new OpenApiDocumentContextImpl(config.name(),
                                                                              config.webContext(),
                                                                              listener,
                                                                              config.generatedMode(),
                                                                              staticOpenApiVersion);
        OpenApiDocument document = staticOpenApiVersion.parse(staticContext, content, contentMediaType);
        return Optional.of(new StaticOpenApiDocument(openApiVersion, document));
    }

    private static Optional<String> openApiVersion(String content, OpenApiFormat format) {
        return switch (format) {
            case JSON -> jsonOpenApiVersion(content);
            case YAML -> yamlOpenApiVersion(content);
            case UNSUPPORTED -> Optional.empty();
        };
    }

    private static Optional<String> jsonOpenApiVersion(String content) {
        JsonObject object = JsonParser.create(content)
                .readJsonValue()
                .asObject();
        return object.stringValue("openapi");
    }

    private static Optional<String> yamlOpenApiVersion(String content) {
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Object version = map.get("openapi");
        if (version == null) {
            return Optional.empty();
        }
        return Optional.of(version.toString());
    }

    private static boolean compatibleOpenApiVersion(OpenApiVersion version, String openApiVersion) {
        String versionFamily = version.type();
        return openApiVersion.equals(version.version())
                || openApiVersion.equals(versionFamily)
                || openApiVersion.startsWith(versionFamily + ".");
    }

    private StaticOpenApiDocument staticOpenApiDocument(String listener) {
        return staticOpenApiDocuments
                .computeIfAbsent(listener, key -> LazyValue.create(() -> parseStaticOpenApiDocument(key)))
                .get()
                .orElseThrow(() ->
                        new IllegalStateException("Static OpenAPI document does not declare an openapi version."));
    }

    private OpenApiVersion staticOpenApiVersion(String staticDocumentVersion) {
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

    private record StaticOpenApiDocument(String openApiVersion, OpenApiDocument document) {
    }
}
