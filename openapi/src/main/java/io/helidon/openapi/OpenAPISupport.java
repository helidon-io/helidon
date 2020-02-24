/*
 * Copyright (c) 2019-2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.openapi;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonString;
import javax.json.JsonValue;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.media.jsonp.server.JsonSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiDocument;
import io.smallrye.openapi.runtime.OpenApiProcessor;
import io.smallrye.openapi.runtime.OpenApiStaticFile;
import io.smallrye.openapi.runtime.io.OpenApiSerializer;
import io.smallrye.openapi.runtime.io.OpenApiSerializer.Format;
import io.smallrye.openapi.runtime.scanner.AnnotationScannerExtension;
import io.smallrye.openapi.runtime.scanner.FilteredIndexView;
import io.smallrye.openapi.runtime.scanner.OpenApiAnnotationScanner;
import org.eclipse.microprofile.openapi.models.Extensible;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Reference;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.servers.ServerVariable;
import org.jboss.jandex.IndexView;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.introspector.Property;

/**
 * Provides an endpoint and supporting logic for returning an OpenAPI document
 * that describes the endpoints handled by the server.
 * <p>
 * The server can use the {@link Builder} to set OpenAPI-related attributes. If
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
    public static final MediaType DEFAULT_RESPONSE_MEDIA_TYPE = MediaType.APPLICATION_OPENAPI_YAML;

    /**
     * Path to the Jandex index file.
     */
    private static final Logger LOGGER = Logger.getLogger(OpenAPISupport.class.getName());

    private static final String DEFAULT_STATIC_FILE_PATH_PREFIX = "META-INF/openapi.";
    private static final String OPENAPI_EXPLICIT_STATIC_FILE_LOG_MESSAGE_FORMAT = "Using specified OpenAPI static file %s";
    private static final String OPENAPI_DEFAULTED_STATIC_FILE_LOG_MESSAGE_FORMAT = "Using default OpenAPI static file %s";

    private static final JsonReaderFactory JSON_READER_FACTORY = Json.createReaderFactory(Collections.emptyMap());

    private static SnakeYAMLParserHelper<ExpandedTypeDescription> helper = null;

    private final String webContext;

    private final OpenAPI model;
    private final ConcurrentMap<Format, String> cachedDocuments = new ConcurrentHashMap<>();
    private final Map<Class<?>, ExpandedTypeDescription> implsToTypes;

    private OpenAPISupport(Builder builder) {
        adjustTypeDescriptions(helper().types());
        implsToTypes = buildImplsToTypes(helper());
        webContext = builder.webContext();
        model = prepareModel(builder.openAPIConfig(), builder.indexView(), builder.staticFile());
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

        rules.get(JsonSupport.create())
                .get(webContext, this::prepareResponse);
    }

    synchronized static SnakeYAMLParserHelper<ExpandedTypeDescription> helper() {
        if (helper == null) {
            helper = SnakeYAMLParserHelper.create(ExpandedTypeDescription::create);
            adjustTypeDescriptions(helper.types());
        }
        return helper;
    }

    static Map<Class<?>, ExpandedTypeDescription> buildImplsToTypes(SnakeYAMLParserHelper<ExpandedTypeDescription> helper) {
        return helper.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getValue().impl(),
                        entry -> entry.getValue()));
    }

    private static void adjustTypeDescriptions(Map<Class<?>, ExpandedTypeDescription> types) {
        ExpandedTypeDescription pathItemTD = types.get(PathItem.class);
        for (PathItem.HttpMethod m : PathItem.HttpMethod.values()) {
            pathItemTD.substituteProperty(m.name().toLowerCase(), Operation.class, getter(m), setter(m));
            pathItemTD.addExcludes(m.name());
        }

        CollectionsHelper.<Class<?>>setOf(Schema.class, ServerVariable.class).forEach(c -> {
            ExpandedTypeDescription tdWithEnumeration = types.get(c);
            tdWithEnumeration.substituteProperty("enum", List.class, "getEnumeration", "setEnumeration");
            tdWithEnumeration.addPropertyParameters("enum", String.class);
            tdWithEnumeration.addExcludes("enumeration");
        });

        for (ExpandedTypeDescription td : types.values()) {
            if (Extensible.class.isAssignableFrom(td.getType())) {
                td.addExtensions();
            }
            if (td.hasDefaultProperty()) {
                td.substituteProperty("default", Object.class, "getDefaultValue", "setDefaultValue");
                td.addExcludes("defaultValue");
            }
            if (isRef(td)) {
                if (isRef(td)) {
                    td.addRef();
                }
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

    /**
     * Prepares the OpenAPI model that later will be used to create the OpenAPI
     * document for endpoints in this application.
     *
     * @param config {@code OpenApiConfig} object describing paths, servers,
     * etc.
     * @return the OpenAPI model
     * @throws RuntimeException in case of errors reading any existing static
     * OpenAPI document
     */
    private OpenAPI prepareModel(OpenApiConfig config, IndexView indexView, OpenApiStaticFile staticFile) {
        try {
            synchronized (OpenApiDocument.INSTANCE) {
                OpenApiDocument.INSTANCE.reset();
                OpenApiDocument.INSTANCE.config(config);
                OpenApiDocument.INSTANCE.modelFromReader(OpenApiProcessor.modelFromReader(config, getContextClassLoader()));
                if (staticFile != null) {
                    OpenApiDocument.INSTANCE.modelFromStaticFile(OpenAPIParser.parse(helper().types(), staticFile.getContent(),
                            OpenAPIMediaType.byFormat(staticFile.getFormat())));
                }
                if (isAnnotationProcessingEnabled(config)) {
                    expandModelUsingAnnotations(config, indexView);
                } else {
                    LOGGER.log(Level.FINE, "OpenAPI Annotation processing is disabled");
                }
                OpenApiDocument.INSTANCE.filter(OpenApiProcessor.getFilter(config, getContextClassLoader()));
                OpenApiDocument.INSTANCE.initialize();
                return OpenApiDocument.INSTANCE.get();
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error initializing OpenAPI information", ex);
        }
    }

    private boolean isAnnotationProcessingEnabled(OpenApiConfig config) {
        return !config.scanDisable();
    }

    private void expandModelUsingAnnotations(OpenApiConfig config, IndexView indexView) throws IOException {
        if (indexView != null) {
            if (config.scanDisable()) {
                return;
            }
            List<AnnotationScannerExtension> scannerExtensions =
                    CollectionsHelper.listOf(new HelidonAnnotationScannerExtension());
            OpenApiAnnotationScanner scanner = new OpenApiAnnotationScanner(config, new FilteredIndexView(indexView, config),
                    scannerExtensions);
            OpenApiDocument.INSTANCE.modelFromAnnotations(scanner.scan());
        }
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    private static String typeFromPath(Path path) {
        final Path staticFileNamePath = path.getFileName();
        if (staticFileNamePath == null) {
            throw new IllegalArgumentException("File path "
                    + path.toAbsolutePath().toString()
                    + " does not seem to have a file name value but one is expected");
        }
        final String pathText = staticFileNamePath.toString();
        final String specifiedFileType = pathText.substring(pathText.lastIndexOf(".") + 1);
        return specifiedFileType;
    }

    private void prepareResponse(ServerRequest req, ServerResponse resp) {

        try {
            final MediaType resultMediaType = chooseResponseMediaType(req);
            final String openAPIDocument = prepareDocument(resultMediaType);
            resp.status(Http.Status.OK_200);
            resp.headers().add(Http.Header.CONTENT_TYPE, resultMediaType.toString());
            resp.send(openAPIDocument);
        } catch (Exception ex) {
            resp.status(Http.Status.INTERNAL_SERVER_ERROR_500);
            resp.send("Error serializing OpenAPI document");
            LOGGER.log(Level.SEVERE, "Error serializing OpenAPI document", ex);
        }
    }

    /**
     * Returns the OpenAPI document in the requested format.
     *
     * @param resultMediaType requested media type
     * @return String containing the formatted OpenAPI document
     * @throws IOException in case of errors serializing the OpenAPI document
     * from its underlying data
     */
    String prepareDocument(MediaType resultMediaType) throws IOException {
        if (model == null) {
            throw new IllegalStateException("OpenAPI model used but has not been initialized");
        }

        OpenAPIMediaType matchingOpenAPIMediaType
                = OpenAPIMediaType.byMediaType(resultMediaType)
                .orElseGet(() -> {
                    LOGGER.log(Level.FINER,
                            () -> String.format(
                                    "Requested media type %s not supported; using default",
                                    resultMediaType.toString()));
                    return OpenAPIMediaType.DEFAULT_TYPE;
                });

        final Format resultFormat = matchingOpenAPIMediaType.format();

        String result = cachedDocuments.computeIfAbsent(resultFormat,
                fmt -> {
                    String r = formatDocument(fmt);
                    LOGGER.log(Level.FINER,
                            "Created and cached OpenAPI document in {0} format",
                            fmt.toString());
                    return r;
                });
        return result;
    }

    private String formatDocument(Format fmt) {
        StringWriter sw = new StringWriter();
        Serializer.serialize(helper().types(), implsToTypes, model, fmt, sw);
        return sw.toString();
    }

    private MediaType chooseResponseMediaType(ServerRequest req) {
        /*
         * Response media type default is application/vnd.oai.openapi (YAML)
         * unless otherwise specified.
         */
        final Optional<MediaType> requestedMediaType = req.headers()
                .bestAccepted(OpenAPIMediaType.preferredOrdering());

        final MediaType resultMediaType = requestedMediaType
                .orElseGet(() -> {
                    LOGGER.log(Level.FINER,
                            () -> String.format("Did not recognize requested media type %s; responding with default %s",
                                    req.headers().acceptedTypes(),
                                    DEFAULT_RESPONSE_MEDIA_TYPE.toString()));
                    return DEFAULT_RESPONSE_MEDIA_TYPE;
                });
        return resultMediaType;
    }

    private static class HelidonAnnotationScannerExtension implements AnnotationScannerExtension {

        @Override
        public Object parseExtension(String key, String value) {

            // Inspired by SmallRye's JsonUtil#parseValue method.
            if (value == null) {
                return null;
            }

            value = value.trim();

            if ("true".equals(value) || "false".equals(value)) {
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
                        LOGGER.log(Level.SEVERE, String.format("Error parsing extension key: %s, value: %s", key, value), ex);
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
                            .map(OpenAPISupport.HelidonAnnotationScannerExtension::convertJsonValue)
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
     * Abstraction of the different representations of a static OpenAPI document
     * file and the file type(s) they correspond to.
     * <p>
     * Each {@code OpenAPIMediaType} stands for a single format (e.g., yaml,
     * json). That said, each can map to multiple file types (e.g., yml and
     * yaml) and multiple actual media types (the proposed OpenAPI media type
     * vnd.oai.openapi and various other YAML types proposed or in use).
     */
    enum OpenAPIMediaType {

        JSON(Format.JSON,
                new MediaType[]{MediaType.APPLICATION_OPENAPI_JSON,
                        MediaType.APPLICATION_JSON},
                "json"),
        YAML(Format.YAML,
                new MediaType[]{MediaType.APPLICATION_OPENAPI_YAML,
                        MediaType.APPLICATION_X_YAML,
                        MediaType.APPLICATION_YAML,
                        MediaType.TEXT_PLAIN,
                        MediaType.TEXT_X_YAML,
                        MediaType.TEXT_YAML},
                "yaml", "yml");

        private static final OpenAPIMediaType DEFAULT_TYPE = YAML;

        private final Format format;
        private final List<String> fileTypes;
        private final List<MediaType> mediaTypes;

        OpenAPIMediaType(Format format, MediaType[] mediaTypes, String... fileTypes) {
            this.format = format;
            this.mediaTypes = Arrays.asList(mediaTypes);
            this.fileTypes = new ArrayList<>(Arrays.asList(fileTypes));
        }

        private OpenApiSerializer.Format format() {
            return format;
        }

        private List<String> matchingTypes() {
            return fileTypes;
        }

        private static OpenAPIMediaType byFileType(String fileType) {
            for (OpenAPIMediaType candidateType : values()) {
                if (candidateType.matchingTypes().contains(fileType)) {
                    return candidateType;
                }
            }
            return null;
        }

        private static Optional<OpenAPIMediaType> byMediaType(MediaType mt) {
            for (OpenAPIMediaType candidateType : values()) {
                if (candidateType.mediaTypes.contains(mt)) {
                    return Optional.of(candidateType);
                }
            }
            return Optional.empty();
        }

        private static List<String> recognizedFileTypes() {
            final List<String> result = new ArrayList<>();
            for (OpenAPIMediaType type : values()) {
                result.addAll(type.fileTypes);
            }
            return result;
        }

        private static OpenAPIMediaType byFormat(Format format) {
            for (OpenAPIMediaType candidateType : values()) {
                if (candidateType.format.equals(format)) {
                    return candidateType;
                }
            }
            return null;
        }

        /**
         * Media types we recognize as OpenAPI, in order of preference.
         *
         * @return MediaTypes in order that we recognize them as OpenAPI
         * content.
         */
        private static MediaType[] preferredOrdering() {
            return new MediaType[]{
                    MediaType.APPLICATION_OPENAPI_YAML,
                    MediaType.APPLICATION_X_YAML,
                    MediaType.APPLICATION_YAML,
                    MediaType.APPLICATION_OPENAPI_JSON,
                    MediaType.APPLICATION_JSON,
                    MediaType.TEXT_PLAIN
            };
        }
    }

    /**
     * Creates a new {@link Builder} for {@code OpenAPISupport} using defaults.
     *
     * @return new Builder
     */
    public static Builder builder() {
        return builderSE();
    }

    /**
     * Creates a new {@link OpenAPISupport} instance using defaults.
     *
     * @return new OpenAPISUpport
     */
    public static OpenAPISupport create() {
        return builderSE().build();
    }

    /**
     * Creates a new {@link OpenAPISupport} instance using the
     * 'openapi' portion of the provided
     * {@link Config} object.
     *
     * @param config {@code Config} object containing OpenAPI-related settings
     * @return new {@code OpenAPISupport} instance created using the
     * helidonConfig settings
     */
    public static OpenAPISupport create(Config config) {
        return builderSE().helidonConfig(config).build();
    }

    /**
     * Returns an OpenAPISupport.Builder for Helidon SE environments.
     *
     * @return Helidon SE {@code OpenAPISupport.Builder}
     */
    static SEOpenAPISupportBuilder builderSE() {
        return new SEOpenAPISupportBuilder();
    }

    /**
     * Fluent API builder for {@link OpenAPISupport}.
     * <p>
     * This abstract implementation is extended once for use by developers from
     * Helidon SE apps and once for use from the Helidon MP-provided OpenAPI
     * service. This lets us constrain what use cases are possible from each
     * (for example, no anno processing from SE).
     */
    public abstract static class Builder implements io.helidon.common.Builder<OpenAPISupport> {

        private Optional<String> webContext = Optional.empty();
        private Optional<String> staticFilePath = Optional.empty();
        private SnakeYAMLParserHelper<ExpandedTypeDescription> helper;

        @Override
        public OpenAPISupport build() {
            validate();
            helper = helper();
            return new OpenAPISupport(this);
        }

        /**
         * Returns the web context (path) at which the OpenAPI endpoint should
         * be exposed, either the most recent explicitly-set value via
         * {@link #webContext(java.lang.String)} or the default
         * {@value #DEFAULT_WEB_CONTEXT}.
         *
         * @return path the web context path for the OpenAPI endpoint
         */
        String webContext() {
            String webContextPath = webContext.orElse(DEFAULT_WEB_CONTEXT);
            if (webContext.isPresent()) {
                LOGGER.log(Level.FINE, "OpenAPI path set to {0}", webContextPath);
            } else {
                LOGGER.log(Level.FINE, "OpenAPI path defaulting to {0}", webContextPath);
            }
            return webContextPath;
        }

        /**
         * Returns the path to a static OpenAPI document file (if any exists),
         * either as explicitly set using {@link #staticFile(java.lang.String) }
         * or one of the default files.
         *
         * @return the OpenAPI static file instance for the static file if such
         * a file exists, null otherwise
         */
        OpenApiStaticFile staticFile() {
            return staticFilePath.isPresent() ? getExplicitStaticFile() : getDefaultStaticFile();
        }

        /**
         * Returns the smallrye OpenApiConfig instance describing the set-up
         * that will govern the smallrye OpenAPI behavior.
         *
         * @return {@code OpenApiConfig} conveying how OpenAPI should behave
         */
        public abstract OpenApiConfig openAPIConfig();

        /**
         * Returns the Jandex {@link IndexView} containing annotated endpoint
         * classes.
         *
         * @return {@code IndexView} containing endpoint classes
         */
        public abstract IndexView indexView();

        /**
         * Makes sure the set-up for OpenAPI is consistent, internally and with
         * the current Helidon runtime environment (SE or MP).
         *
         * @throws IllegalStateException if validation fails
         */
        public void validate() throws IllegalStateException {
        }

        /**
         * Path under which to register OpenAPI endpoint on the web server.
         *
         * @param path webContext to use, defaults to
         * {@value DEFAULT_WEB_CONTEXT}
         * @return updated builder instance
         */
        public Builder webContext(String path) {
            this.webContext = Optional.of(path);
            return this;
        }

        /**
         * Sets the location of the static OpenAPI document file.
         *
         * @param path non-null location of the static OpenAPI document file
         * @return updated builder instance
         */
        public Builder staticFile(String path) {
            Objects.requireNonNull(path, "path to static file must be non-null");
            staticFilePath = Optional.of(path);
            return this;
        }

        private OpenApiStaticFile getExplicitStaticFile() {
            Path path = Paths.get(staticFilePath.get());
            final String specifiedFileType = typeFromPath(path);
            final OpenAPIMediaType specifiedMediaType = OpenAPIMediaType.byFileType(specifiedFileType);

            if (specifiedMediaType == null) {
                throw new IllegalArgumentException("OpenAPI file path "
                        + path.toAbsolutePath().toString()
                        + " is not one of recognized types: "
                        + OpenAPIMediaType.recognizedFileTypes());
            }
            final InputStream is;
            try {
                is = new BufferedInputStream(Files.newInputStream(path));
            } catch (IOException ex) {
                throw new IllegalArgumentException("OpenAPI file "
                        + path.toAbsolutePath().toString()
                        + " was specified but was not found", ex);
            }

            try {
                LOGGER.log(Level.FINE,
                        () -> String.format(
                                OPENAPI_EXPLICIT_STATIC_FILE_LOG_MESSAGE_FORMAT,
                                path.toAbsolutePath().toString()));
                return new OpenApiStaticFile(is, specifiedMediaType.format());
            } catch (Exception ex) {
                try {
                    is.close();
                } catch (IOException ioex) {
                    ex.addSuppressed(ioex);
                }
                throw ex;
            }
        }

        private OpenApiStaticFile getDefaultStaticFile() {
            final List<String> candidatePaths = LOGGER.isLoggable(Level.FINER) ? new ArrayList<>() : null;
            for (OpenAPIMediaType candidate : OpenAPIMediaType.values()) {
                for (String type : candidate.matchingTypes()) {
                    String candidatePath = DEFAULT_STATIC_FILE_PATH_PREFIX + type;
                    InputStream is = null;
                    try {
                        is = getContextClassLoader().getResourceAsStream(candidatePath);
                        if (is != null) {
                            Path path = Paths.get(candidatePath);
                            LOGGER.log(Level.FINE, () -> String.format(
                                    OPENAPI_DEFAULTED_STATIC_FILE_LOG_MESSAGE_FORMAT,
                                    path.toAbsolutePath().toString()));
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
                LOGGER.log(Level.FINER,
                        candidatePaths.stream()
                                .collect(Collectors.joining(
                                        "No default static OpenAPI description file found; checked [",
                                        ",",
                                        "]")));
            }
            return null;
        }
    }
}
