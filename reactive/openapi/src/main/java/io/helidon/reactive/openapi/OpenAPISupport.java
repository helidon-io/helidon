/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.reactive.openapi;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.common.http.Http;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.openapi.ExpandedTypeDescription;
import io.helidon.openapi.OpenAPIMediaType;
import io.helidon.openapi.OpenAPIParser;
import io.helidon.openapi.ParserHelper;
import io.helidon.openapi.Serializer;
import io.helidon.openapi.internal.OpenAPIConfigImpl;
import io.helidon.reactive.media.common.MessageBodyReaderContext;
import io.helidon.reactive.media.common.MessageBodyWriterContext;
import io.helidon.reactive.media.jsonp.JsonpSupport;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.reactive.webserver.Service;
import io.helidon.reactive.webserver.cors.CorsEnabledServiceHelper;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiDocument;
import io.smallrye.openapi.api.models.OpenAPIImpl;
import io.smallrye.openapi.api.util.MergeUtil;
import io.smallrye.openapi.runtime.OpenApiProcessor;
import io.smallrye.openapi.runtime.OpenApiStaticFile;
import io.smallrye.openapi.runtime.io.Format;
import io.smallrye.openapi.runtime.scanner.AnnotationScannerExtension;
import io.smallrye.openapi.runtime.scanner.OpenApiAnnotationScanner;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.openapi.models.Extensible;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Reference;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.servers.ServerVariable;
import org.jboss.jandex.IndexView;
import org.yaml.snakeyaml.TypeDescription;

import static io.helidon.reactive.webserver.cors.CorsEnabledServiceHelper.CORS_CONFIG_KEY;

/**
 * Provides an endpoint and supporting logic for returning an OpenAPI document
 * that describes the endpoints handled by the server.
 * <p>
 * The server can use the {@link io.helidon.reactive.openapi.OpenAPISupport.Builder} to set OpenAPI-related attributes. If
 * the server uses none of these builder methods and does not provide a static
 * {@code openapi} file, then the {@code /openapi} endpoint responds with a
 * nearly-empty OpenAPI document.
 */
public class OpenAPISupport implements Service {

    /**
     * Default path for serving the OpenAPI document.
     */
    public static final String DEFAULT_WEB_CONTEXT = "/openapi";

    /**
     * Default media type used in responses in absence of incoming Accept
     * header.
     */
    public static final MediaType DEFAULT_RESPONSE_MEDIA_TYPE = MediaTypes.APPLICATION_OPENAPI_YAML;
    private static final String OPENAPI_ENDPOINT_FORMAT_QUERY_PARAMETER = "format";
    private static final System.Logger LOGGER = System.getLogger(OpenAPISupport.class.getName());
    private static final String DEFAULT_STATIC_FILE_PATH_PREFIX = "META-INF/openapi.";
    private static final String OPENAPI_EXPLICIT_STATIC_FILE_LOG_MESSAGE_FORMAT = "Using specified OpenAPI static file %s";
    private static final String OPENAPI_DEFAULTED_STATIC_FILE_LOG_MESSAGE_FORMAT = "Using default OpenAPI static file %s";
    private static final String FEATURE_NAME = "OpenAPI";
    private static final JsonReaderFactory JSON_READER_FACTORY = Json.createReaderFactory(Collections.emptyMap());
    private static final LazyValue<ParserHelper> HELPER = LazyValue.create(ParserHelper::create);

    private final String webContext;
    private final ConcurrentMap<Format, String> cachedDocuments = new ConcurrentHashMap<>();
    private final Map<Class<?>, ExpandedTypeDescription> implsToTypes;
    private final CorsEnabledServiceHelper corsEnabledServiceHelper;
    /*
     * To handle the MP case, we must defer constructing the OpenAPI in-memory model until after the server has instantiated
     * the Application instances. By then the builder has already been used to build the OpenAPISupport object. So save the
     * following raw materials so we can construct the model at that later time.
     */
    private final OpenApiConfig openApiConfig;
    private final OpenApiStaticFile openApiStaticFile;
    private final Supplier<List<? extends IndexView>> indexViewsSupplier;
    private final Lock modelAccess = new ReentrantLock(true);
    private OpenAPI model = null;

    /**
     * Creates a new instance of {@code OpenAPISupport}.
     *
     * @param builder the builder to use in constructing the instance
     */
    protected OpenAPISupport(Builder builder) {
        implsToTypes = ExpandedTypeDescription.buildImplsToTypes(HELPER.get());
        webContext = builder.webContext();
        corsEnabledServiceHelper = CorsEnabledServiceHelper.create(FEATURE_NAME, builder.crossOriginConfig);
        openApiConfig = builder.openAPIConfig();
        openApiStaticFile = builder.staticFile();
        indexViewsSupplier = builder.indexViewsSupplier();
    }

    /**
     * Creates a new {@link io.helidon.reactive.openapi.OpenAPISupport.Builder} for {@code OpenAPISupport} using defaults.
     *
     * @return new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new {@link io.helidon.reactive.openapi.OpenAPISupport} instance using defaults.
     *
     * @return new OpenAPISUpport
     */
    public static OpenAPISupport create() {
        return builder().build();
    }

    /**
     * Creates a new {@link io.helidon.reactive.openapi.OpenAPISupport} instance using the
     * 'openapi' portion of the provided
     * {@link io.helidon.config.Config} object.
     *
     * @param config {@code Config} object containing OpenAPI-related settings
     * @return new {@code OpenAPISupport} instance created using the
     *         helidonConfig settings
     */
    public static OpenAPISupport create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public void update(Routing.Rules rules) {
        configureEndpoint(rules);
    }

    /**
     * Sets up the OpenAPI endpoint by adding routing to the specified rules
     * set.
     *
     * @param rules routing rules to be augmented with OpenAPI endpoint
     */
    public void configureEndpoint(Routing.Rules rules) {

        rules.get(this::registerJsonpSupport)
                .any(webContext, corsEnabledServiceHelper.processor())
                .get(webContext, this::prepareResponse);
    }

    /**
     * Triggers preparation of the model from external code.
     */
    protected void prepareModel() {
        model();
    }

    /**
     * Returns the OpenAPI document in the requested format.
     *
     * @param resultMediaType requested media type
     * @return String containing the formatted OpenAPI document
     * @throws java.io.IOException in case of errors serializing the OpenAPI document
     *                             from its underlying data
     */
    String prepareDocument(MediaType resultMediaType) {
        OpenAPIMediaType matchingOpenAPIMediaType
                = OpenAPIMediaType.byMediaType(resultMediaType)
                .orElseGet(() -> {
                    LOGGER.log(Level.TRACE,
                               () -> String.format(
                                       "Requested media type %s not supported; using default",
                                       resultMediaType.text()));
                    return OpenAPIMediaType.DEFAULT_TYPE;
                });

        Format resultFormat = matchingOpenAPIMediaType.format();

        String result = cachedDocuments.computeIfAbsent(resultFormat,
                                                        fmt -> {
                                                            String r = formatDocument(fmt);
                                                            LOGGER.log(Level.TRACE,
                                                                       "Created and cached OpenAPI document in {0} format",
                                                                       fmt.toString());
                                                            return r;
                                                        });
        return result;
    }

    private static void adjustTypeDescriptions(Map<Class<?>, ExpandedTypeDescription> types) {
        /*
         * We need to adjust the {@code TypeDescription} objects set up by the generated {@code SnakeYAMLParserHelper} class
         * because there are some OpenAPI-specific issues that the general-purpose helper generator cannot know about.
         */

        /*
         * In the OpenAPI document, HTTP methods are expressed in lower-case. But the associated Java methods on the PathItem
         * class use the HTTP method names in upper-case. So for each HTTP method, "add" a property to PathItem's type
         * description using the lower-case name but upper-case Java methods and exclude the upper-case property that
         * SnakeYAML's automatic analysis of the class already created.
         */
        ExpandedTypeDescription pathItemTD = types.get(PathItem.class);
        for (PathItem.HttpMethod m : PathItem.HttpMethod.values()) {
            pathItemTD.substituteProperty(m.name().toLowerCase(), Operation.class, getter(m), setter(m));
            pathItemTD.addExcludes(m.name());
        }

        /*
         * An OpenAPI document can contain a property named "enum" for Schema and ServerVariable, but the related Java methods
         * use "enumeration".
         */
        Set.<Class<?>>of(Schema.class, ServerVariable.class).forEach(c -> {
            ExpandedTypeDescription tdWithEnumeration = types.get(c);
            tdWithEnumeration.substituteProperty("enum", List.class, "getEnumeration", "setEnumeration");
            tdWithEnumeration.addPropertyParameters("enum", String.class);
            tdWithEnumeration.addExcludes("enumeration");
        });

        /*
         * SnakeYAML derives properties only from methods declared directly by each OpenAPI interface, not from methods defined
         *  on other interfaces which the original one extends. Those we have to handle explicitly.
         */
        for (ExpandedTypeDescription td : types.values()) {
            if (Extensible.class.isAssignableFrom(td.getType())) {
                td.addExtensions();
            }
            if (td.hasDefaultProperty()) {
                td.substituteProperty("default", Object.class, "getDefaultValue", "setDefaultValue");
                td.addExcludes("defaultValue");
            }
            if (isRef(td)) {
                td.addRef();
            }
        }
    }

    private static boolean isRef(TypeDescription td) {
        for (Class<?> c : td.getType().getInterfaces()) {
            if (c.equals(Reference.class)) {
                return true;
            }
        }
        return false;
    }

    private static String getter(PathItem.HttpMethod method) {
        return methodName("get", method);
    }

    private static String setter(PathItem.HttpMethod method) {
        return methodName("set", method);
    }

    private static String methodName(String operation, PathItem.HttpMethod method) {
        return operation + method.name();
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    private static String typeFromPath(Path path) {
        Path staticFileNamePath = path.getFileName();
        if (staticFileNamePath == null) {
            throw new IllegalArgumentException("File path "
                                                       + path.toAbsolutePath()
                                                       + " does not seem to have a file name value but one is expected");
        }
        String pathText = staticFileNamePath.toString();
        String specifiedFileType = pathText.substring(pathText.lastIndexOf(".") + 1);
        return specifiedFileType;
    }

    private static <T> T access(Lock guard, Supplier<T> operation) {
        guard.lock();
        try {
            return operation.get();
        } finally {
            guard.unlock();
        }
    }

    private OpenAPI model() {
        return access(modelAccess, () -> {
            if (model == null) {
                model = prepareModel(openApiConfig, openApiStaticFile, indexViewsSupplier.get());
            }
            return model;
        });
    }

    private void registerJsonpSupport(ServerRequest req, ServerResponse res) {
        MessageBodyReaderContext readerContext = req.content().readerContext();
        MessageBodyWriterContext writerContext = res.writerContext();
        JsonpSupport.create().register(readerContext, writerContext);
        req.next();
    }

    /**
     * Prepares the OpenAPI model that later will be used to create the OpenAPI
     * document for endpoints in this application.
     *
     * @param config             {@code OpenApiConfig} object describing paths, servers, etc.
     * @param staticFile         the static file, if any, to be included in the resulting model
     * @param filteredIndexViews possibly empty list of FilteredIndexViews to use in harvesting definitions from the code
     * @return the OpenAPI model
     * @throws RuntimeException in case of errors reading any existing static
     *                          OpenAPI document
     */
    private OpenAPI prepareModel(OpenApiConfig config, OpenApiStaticFile staticFile,
                                 List<? extends IndexView> filteredIndexViews) {
        try {
            // The write lock guarding the model has already been acquired.
            OpenApiDocument.INSTANCE.reset();
            OpenApiDocument.INSTANCE.config(config);
            OpenApiDocument.INSTANCE.modelFromReader(OpenApiProcessor.modelFromReader(config, getContextClassLoader()));
            if (staticFile != null) {
                OpenApiDocument.INSTANCE.modelFromStaticFile(OpenAPIParser.parse(HELPER.get().types(), staticFile.getContent()));
            }
            if (isAnnotationProcessingEnabled(config)) {
                expandModelUsingAnnotations(config, filteredIndexViews);
            } else {
                LOGGER.log(Level.DEBUG, "OpenAPI Annotation processing is disabled");
            }
            OpenApiDocument.INSTANCE.filter(OpenApiProcessor.getFilter(config, getContextClassLoader()));
            OpenApiDocument.INSTANCE.initialize();
            OpenAPIImpl instance = OpenAPIImpl.class.cast(OpenApiDocument.INSTANCE.get());

            // Create a copy, primarily to avoid problems during unit testing.
            // The SmallRye MergeUtil omits the openapi value, so we need to set it explicitly.
            return MergeUtil.merge(new OpenAPIImpl(), instance)
                    .openapi(instance.getOpenapi());
        } catch (IOException ex) {
            throw new RuntimeException("Error initializing OpenAPI information", ex);
        }
    }

    private boolean isAnnotationProcessingEnabled(OpenApiConfig config) {
        return !config.scanDisable();
    }

    private void expandModelUsingAnnotations(OpenApiConfig config, List<? extends IndexView> filteredIndexViews) {
        if (filteredIndexViews.isEmpty() || config.scanDisable()) {
            return;
        }

        /*
         * Conduct a SmallRye OpenAPI annotation scan for each filtered index view, merging the resulting OpenAPI models into one.
         * The AtomicReference is effectively final so we can update the actual reference from inside the lambda.
         */
        AtomicReference<OpenAPI> aggregateModelRef = new AtomicReference<>(new OpenAPIImpl()); // Start with skeletal model
        filteredIndexViews.forEach(filteredIndexView -> {
            OpenApiAnnotationScanner scanner = new OpenApiAnnotationScanner(config, filteredIndexView,
                                                                            List.of(new HelidonAnnotationScannerExtension()));
            OpenAPI modelForApp = scanner.scan();
            if (LOGGER.isLoggable(Level.TRACE)) {

                LOGGER.log(Level.TRACE, String.format("Intermediate model from filtered index view %s:%n%s",
                                                      filteredIndexView.getKnownClasses(),
                                                      formatDocument(Format.YAML, modelForApp)));
            }
            aggregateModelRef.set(
                    MergeUtil.merge(aggregateModelRef.get(), modelForApp)
                            .openapi(modelForApp.getOpenapi())); // SmallRye's merge skips openapi value.

        });
        OpenApiDocument.INSTANCE.modelFromAnnotations(aggregateModelRef.get());
    }

    private void prepareResponse(ServerRequest req, ServerResponse resp) {

        try {
            MediaType resultMediaType = chooseResponseMediaType(req);
            String openAPIDocument = prepareDocument(resultMediaType);
            resp.status(Http.Status.OK_200);
            resp.headers().add(Http.Header.CONTENT_TYPE, resultMediaType.text());
            resp.send(openAPIDocument);
        } catch (Exception ex) {
            resp.status(Http.Status.INTERNAL_SERVER_ERROR_500);
            resp.send("Error serializing OpenAPI document; " + ex.getMessage());
            LOGGER.log(Level.ERROR, "Error serializing OpenAPI document", ex);
        }
    }

    private String formatDocument(Format fmt) {
        return formatDocument(fmt, model());
    }

    private String formatDocument(Format fmt, OpenAPI model) {
        StringWriter sw = new StringWriter();
        Serializer.serialize(HELPER.get().types(), implsToTypes, model, fmt, sw);
        return sw.toString();

    }

    private MediaType chooseResponseMediaType(ServerRequest req) {
        /*
         * Response media type default is application/vnd.oai.openapi (YAML)
         * unless otherwise specified.
         */
        Optional<String> queryParameterFormat = req.queryParams()
                .first(OPENAPI_ENDPOINT_FORMAT_QUERY_PARAMETER);
        if (queryParameterFormat.isPresent()) {
            String queryParameterFormatValue = queryParameterFormat.get();
            try {
                return QueryParameterRequestedFormat.chooseFormat(queryParameterFormatValue).mediaType();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Query parameter 'format' had value '"
                                + queryParameterFormatValue
                                + "' but expected " + Arrays.toString(QueryParameterRequestedFormat.values()));
            }
        }

        Optional<MediaType> requestedMediaType = req.headers()
                .bestAccepted(OpenAPIMediaType.preferredOrdering());

        MediaType resultMediaType = requestedMediaType
                .orElseGet(() -> {
                    LOGGER.log(Level.TRACE,
                               () -> String.format("Did not recognize requested media type %s; responding with default %s",
                                                   req.headers().acceptedTypes(),
                                                   DEFAULT_RESPONSE_MEDIA_TYPE.text()));
                    return DEFAULT_RESPONSE_MEDIA_TYPE;
                });
        return resultMediaType;
    }

    private enum QueryParameterRequestedFormat {
        JSON(MediaTypes.APPLICATION_JSON), YAML(MediaTypes.APPLICATION_OPENAPI_YAML);

        private final MediaType mt;

        QueryParameterRequestedFormat(MediaType mt) {
            this.mt = mt;
        }

        static QueryParameterRequestedFormat chooseFormat(String format) {
            return QueryParameterRequestedFormat.valueOf(format);
        }

        MediaType mediaType() {
            return mt;
        }
    }

    /**
     * Extension we want SmallRye's OpenAPI implementation to use for parsing the JSON content in Extension annotations.
     */
    private static class HelidonAnnotationScannerExtension implements AnnotationScannerExtension {

        @Override
        public Object parseExtension(String key, String value) {

            // Inspired by SmallRye's JsonUtil#parseValue method.
            if (value == null) {
                return null;
            }

            value = value.trim();

            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return Boolean.valueOf(value);
            }

            // See if we should parse the value fully.
            switch (value.charAt(0)) {
            case '{':
            case '[':
            case '-':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                try {
                    JsonReader reader = JSON_READER_FACTORY.createReader(new StringReader(value));
                    JsonValue jsonValue = reader.readValue();
                    return convertJsonValue(jsonValue);
                } catch (Exception ex) {
                    LOGGER.log(Level.ERROR, String.format("Error parsing extension key: %s, value: %s", key, value), ex);
                }
                break;

            default:
                break;
            }

            // Treat as JSON string.
            return value;
        }

        private static Object convertJsonValue(JsonValue jsonValue) {
            switch (jsonValue.getValueType()) {
            case ARRAY:
                JsonArray jsonArray = jsonValue.asJsonArray();
                return jsonArray.stream()
                        .map(HelidonAnnotationScannerExtension::convertJsonValue)
                        .collect(Collectors.toList());

            case FALSE:
                return Boolean.FALSE;

            case TRUE:
                return Boolean.TRUE;

            case NULL:
                return null;

            case STRING:
                return JsonString.class.cast(jsonValue).getString();

            case NUMBER:
                JsonNumber jsonNumber = JsonNumber.class.cast(jsonValue);
                return jsonNumber.numberValue();

            case OBJECT:
                JsonObject jsonObject = jsonValue.asJsonObject();
                return jsonObject.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> convertJsonValue(entry.getValue())));

            default:
                return jsonValue.toString();
            }
        }
    }

    /**
     * Fluent API builder for {@link io.helidon.reactive.openapi.OpenAPISupport}.
     */
    @Configured(description = "OpenAPI support configuration")
    public static class Builder implements io.helidon.common.Builder<Builder, OpenAPISupport> {

        /**
         * Config key to select the openapi node from Helidon config.
         */
        public static final String CONFIG_KEY = "openapi";

        private final OpenAPIConfigImpl.Builder apiConfigBuilder = OpenAPIConfigImpl.builder();
        private String webContext;
        private String staticFilePath;
        private CrossOriginConfig crossOriginConfig = null;

        private Builder() {
        }

        @Override
        public OpenAPISupport build() {
            OpenAPISupport openAPISupport = new OpenAPISupport(this);
            openAPISupport.prepareModel();
            return openAPISupport;
        }

        /**
         * Set various builder attributes from the specified {@code Config} object.
         * <p>
         * The {@code Config} object can specify web-context and static-file in addition to settings
         * supported by {@link io.helidon.openapi.internal.OpenAPIConfigImpl.Builder}.
         *
         * @param config the openapi {@code Config} object possibly containing settings
         * @return updated builder instance
         * @throws NullPointerException if the provided {@code Config} is null
         */
        @ConfiguredOption(type = OpenApiConfig.class)
        public Builder config(Config config) {
            config.get("web-context")
                    .asString()
                    .ifPresent(this::webContext);
            config.get("static-file")
                    .asString()
                    .ifPresent(this::staticFile);
            config.get(CORS_CONFIG_KEY)
                    .as(CrossOriginConfig::create)
                    .ifPresent(this::crossOriginConfig);
            return this;
        }

        /**
         * Makes sure the set-up for OpenAPI is consistent, internally and with
         * the current Helidon runtime environment (SE or MP).
         *
         * @throws IllegalStateException if validation fails
         */
        public void validate() throws IllegalStateException {
        }

        /**
         * Sets the web context path for the OpenAPI endpoint.
         *
         * @param path webContext to use, defaults to
         *             {@value DEFAULT_WEB_CONTEXT}
         * @return updated builder instance
         */
        @ConfiguredOption(DEFAULT_WEB_CONTEXT)
        public Builder webContext(String path) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            this.webContext = path;
            return this;
        }

        /**
         * Sets the file system path of the static OpenAPI document file. Default types are `json`, `yaml`, and `yml`.
         *
         * @param path non-null location of the static OpenAPI document file
         * @return updated builder instance
         */
        @ConfiguredOption(value = DEFAULT_STATIC_FILE_PATH_PREFIX + "*")
        public Builder staticFile(String path) {
            Objects.requireNonNull(path, "path to static file must be non-null");
            staticFilePath = path;
            return this;
        }

        /**
         * Assigns the CORS settings for the OpenAPI endpoint.
         *
         * @param crossOriginConfig {@code CrossOriginConfig} containing CORS set-up
         * @return updated builder instance
         */
        @ConfiguredOption(key = CORS_CONFIG_KEY)
        public Builder crossOriginConfig(CrossOriginConfig crossOriginConfig) {
            Objects.requireNonNull(crossOriginConfig, "CrossOriginConfig must be non-null");
            this.crossOriginConfig = crossOriginConfig;
            return this;
        }

        /**
         * Sets the app-provided model reader class.
         *
         * @param className name of the model reader class
         * @return updated builder instance
         */
        public Builder modelReader(String className) {
            Objects.requireNonNull(className, "modelReader class name must be non-null");
            apiConfigBuilder.modelReader(className);
            return this;
        }

        /**
         * Set the app-provided OpenAPI model filter class.
         *
         * @param className name of the filter class
         * @return updated builder instance
         */
        public Builder filter(String className) {
            Objects.requireNonNull(className, "filter class name must be non-null");
            apiConfigBuilder.filter(className);
            return this;
        }

        /**
         * Sets the servers which offer the endpoints in the OpenAPI document.
         *
         * @param serverList comma-separated list of servers
         * @return updated builder instance
         */
        public Builder servers(String serverList) {
            Objects.requireNonNull(serverList, "serverList must be non-null");
            apiConfigBuilder.servers(serverList);
            return this;
        }

        /**
         * Adds an operation server for a given operation ID.
         *
         * @param operationID     operation ID to which the server corresponds
         * @param operationServer name of the server to add for this operation
         * @return updated builder instance
         */
        public Builder addOperationServer(String operationID, String operationServer) {
            Objects.requireNonNull(operationID, "operationID must be non-null");
            Objects.requireNonNull(operationServer, "operationServer must be non-null");
            apiConfigBuilder.addOperationServer(operationID, operationServer);
            return this;
        }

        /**
         * Adds a path server for a given path.
         *
         * @param path       path to which the server corresponds
         * @param pathServer name of the server to add for this path
         * @return updated builder instance
         */
        public Builder addPathServer(String path, String pathServer) {
            Objects.requireNonNull(path, "path must be non-null");
            Objects.requireNonNull(pathServer, "pathServer must be non-null");
            apiConfigBuilder.addPathServer(path, pathServer);
            return this;
        }

        /**
         * Returns the supplier of index views.
         *
         * @return index views supplier
         */
        protected Supplier<List<? extends IndexView>> indexViewsSupplier() {
            // Only in MP can we have possibly multiple index views, one per app, from scanning classes (or the Jandex index).
            return List::of;
        }

        /**
         * Returns the smallrye OpenApiConfig instance describing the set-up
         * that will govern the smallrye OpenAPI behavior.
         *
         * @return {@code OpenApiConfig} conveying how OpenAPI should behave
         */
        OpenApiConfig openAPIConfig() {
            return apiConfigBuilder.build();
        }

        /**
         * Returns the web context (path) at which the OpenAPI endpoint should
         * be exposed, either the most recent explicitly-set value via
         * {@link #webContext(String)} or the default
         * {@value #DEFAULT_WEB_CONTEXT}.
         *
         * @return path the web context path for the OpenAPI endpoint
         */
        String webContext() {
            String webContextPath = webContext == null ? DEFAULT_WEB_CONTEXT : webContext;
            if (webContext == null) {
                LOGGER.log(Level.DEBUG, "OpenAPI path defaulting to {0}", webContextPath);
            } else {
                LOGGER.log(Level.DEBUG, "OpenAPI path set to {0}", webContextPath);
            }
            return webContextPath;
        }

        /**
         * Returns the path to a static OpenAPI document file (if any exists),
         * either as explicitly set using {@link #staticFile(String) }
         * or one of the default files.
         *
         * @return the OpenAPI static file instance for the static file if such
         *         a file exists, null otherwise
         */
        OpenApiStaticFile staticFile() {
            return staticFilePath == null ? getDefaultStaticFile() : getExplicitStaticFile();
        }

        private OpenApiStaticFile getExplicitStaticFile() {
            Path path = Paths.get(staticFilePath);
            String specifiedFileType = typeFromPath(path);
            OpenAPIMediaType specifiedMediaType = OpenAPIMediaType.byFileType(specifiedFileType)
                    .orElseThrow(() -> new IllegalArgumentException("OpenAPI file path "
                                                           + path.toAbsolutePath()
                                                           + " is not one of recognized types: "
                                                           + OpenAPIMediaType.recognizedFileTypes()));

            try {
                InputStream is = new BufferedInputStream(Files.newInputStream(path));
                LOGGER.log(Level.DEBUG,
                           () -> String.format(
                                   OPENAPI_EXPLICIT_STATIC_FILE_LOG_MESSAGE_FORMAT,
                                   path.toAbsolutePath()));
                return new OpenApiStaticFile(is, specifiedMediaType.format());
            } catch (IOException ex) {
                throw new IllegalArgumentException("OpenAPI file "
                                                           + path.toAbsolutePath()
                                                           + " was specified but was not found", ex);
            }
        }

        private OpenApiStaticFile getDefaultStaticFile() {
            List<String> candidatePaths = LOGGER.isLoggable(Level.TRACE) ? new ArrayList<>() : null;
            for (OpenAPIMediaType candidate : OpenAPIMediaType.values()) {
                for (String type : candidate.matchingTypes()) {
                    String candidatePath = DEFAULT_STATIC_FILE_PATH_PREFIX + type;
                    InputStream is = null;
                    try {
                        is = getContextClassLoader().getResourceAsStream(candidatePath);
                        if (is != null) {
                            Path path = Paths.get(candidatePath);
                            LOGGER.log(Level.DEBUG, () -> String.format(
                                    OPENAPI_DEFAULTED_STATIC_FILE_LOG_MESSAGE_FORMAT,
                                    path.toAbsolutePath()));
                            return new OpenApiStaticFile(is, candidate.format());
                        }
                        if (candidatePaths != null) {
                            candidatePaths.add(candidatePath);
                        }
                    } catch (Exception ex) {
                        if (is != null) {
                            try {
                                is.close();
                            } catch (IOException ioex) {
                                ex.addSuppressed(ioex);
                            }
                        }
                        throw ex;
                    }
                }
            }
            if (candidatePaths != null) {
                LOGGER.log(Level.TRACE,
                           candidatePaths.stream()
                                   .collect(Collectors.joining(
                                           ",",
                                           "No default static OpenAPI description file found; checked [",
                                           "]")));
            }
            return null;
        }
    }
}
