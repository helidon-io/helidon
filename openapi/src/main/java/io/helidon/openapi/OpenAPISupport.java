/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
import org.jboss.jandex.IndexReader;

/**
 * Provides an endpoint and supporting logic for returning an OpenAPI document
 * that describes the endpoints handled by the application.
 * <p>
 * The application can use the {@link Builder} to set OpenAPI-related
 * attributes, including:
 * <table>
 * <caption>OpenAPI-related Settings</caption>
 * <tr>
 * <th>Method on {@link Builder}</th>
 * <th>Purpose</th>
 * </tr>
 * <tr>
 * <td>{@link Builder#config}</td>
 * <td>sets multiple OpenAPI-related items from a {@link Config} object</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#staticFile}</td>
 * <td>sets the static OpenAPI document file (defaults in order to {@code openapi.yaml},
 * {@code openapi.yml}, or {@code openapi.json})</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#enableAnnotationProcessing}</td>
 * <td>sets whether OpenAPI annotations should be processed</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#webContext}</td>
 * <td>sets the endpoint path that will serve the OpenAPI document</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#modelReader}</td>
 * <td>sets the application-provided class for adding to the OpenAPI document</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#filter}</td>
 * <td>sets the application-provided class for filtering OpenAPI document information</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#servers}</td>
 * <td>sets the servers to be reported in the OpenAPI document</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#addOperationServer}</td>
 * <td>associates a server with a given operation ID</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#addPathServer}</td>
 * <td>associates a server with a given path</td>
 * </tr>
 * </table>
 * If the application uses none of these builder methods and does not provide
 * a static {@code openapi} file, then the {@code /openapi} endpoint responds with
 * a nearly-empty OpenAPI document.
 */
public class OpenAPISupport implements Service {

    /**
     * Default path for serving the OpenAPI document.
     */
    public static final String DEFAULT_WEB_CONTEXT = "/openapi";

    public static final MediaType DEFAULT_RESPONSE_MEDIA_TYPE =  MediaType.APPLICATION_OPENAPI_YAML;

    private static final Logger LOGGER = Logger.getLogger(OpenAPISupport.class.getName());

    private static final String DEFAULT_STATIC_FILE_PATH_PREFIX = "/openapi.";
    private static final String OPENAPI_EXPLICIT_STATIC_FILE_LOG_MESSAGE_FORMAT = "Using specified OpenAPI static file %s";
    private static final String OPENAPI_DEFAULTED_STATIC_FILE_LOG_MESSAGE_FORMAT = "Using default OpenAPI static file %s";

    private static final String JANDEX_INDEX_PATH = "/META-INF/jandex.idx";

    private final Optional<String> webContext;
    private final Optional<String> staticFilePath;
    private final OpenApiConfig openAPIConfig;

    private boolean isAnnotationProcessingEnabled = false;

    private final Map<MediaType, String> cachedDocuments = new HashMap<>();

    private OpenAPISupport(final Builder builder) {
        webContext = builder.webContext;
        staticFilePath = builder.staticFilePath;
        this.isAnnotationProcessingEnabled = builder.isAnnotationProcessingEnabled;
        this.openAPIConfig = builder.apiConfigBuilder.build();
    }

    @Override
    public void update(final Routing.Rules rules) {
        try {
            initializeOpenAPIDocument(openAPIConfig);
        } catch (IOException ex) {
            throw new RuntimeException("Error initializing OpenAPI information", ex);
        }
        String webContextPath = DEFAULT_WEB_CONTEXT;
        if (webContext.isPresent()) {
            webContextPath = webContext.get();
            LOGGER.log(Level.FINE, "OpenAPI path set to {0}", webContextPath);
        } else {
            LOGGER.log(Level.FINE, "OpenAPI path defaulting to {0}", webContextPath);
        }
        rules.get(JsonSupport.create())
                .get(webContextPath, this::prepareResponse);
    }

    /**
     * Prepares the information used to create the OpenAPI document for endpoints
     * in this application.
     *
     * @param config {@code OpenApiConfig} object describing paths, servers, etc.
     * @throws IOException in case of errors reading any existing static OpenAPI document
     */
    void initializeOpenAPIDocument(final OpenApiConfig config) throws IOException {
        try (OpenApiStaticFile staticFile = buildOpenAPIStaticFile()) {
            OpenApiDocument.INSTANCE.reset();
            OpenApiDocument.INSTANCE.config(config);
            OpenApiDocument.INSTANCE.modelFromReader(OpenApiProcessor.modelFromReader(config, getContextClassLoader()));
            OpenApiDocument.INSTANCE.modelFromStaticFile(OpenApiProcessor.modelFromStaticFile(staticFile));
            if (isAnnotationProcessingEnabled()) {
                    expandModelUsingAnnotations(config);
            } else {
                LOGGER.log(Level.FINE, "OpenAPI Annotation processing is disabled");
            }
            OpenApiDocument.INSTANCE.filter(OpenApiProcessor.getFilter(config, getContextClassLoader()));
            OpenApiDocument.INSTANCE.initialize();
        }
    }

    /**
     * Reports whether annotation processing is always off, regardless of
     * configuration or builder settings.
     * <p>
     * In Helidion SE, annotation scanning is always off for OpenAPI.
     *
     * @return whether annotation processing is always disabled
     */
    boolean isAnnotationProcessingAlwaysDisabled() {
        return true;
    }

    private boolean isAnnotationProcessingEnabled() {
        return !isAnnotationProcessingAlwaysDisabled() && isAnnotationProcessingEnabled;
    }

    private void expandModelUsingAnnotations(final OpenApiConfig config) throws IOException {
        try (InputStream jandexIS = getClass().getResourceAsStream(JANDEX_INDEX_PATH)) {
            if (jandexIS == null) {
                LOGGER.log(Level.FINE,
                        "OpenAPI Annotation processing enabled but jandex index {0} not found; "
                        + "continuing without scanning for OpenAPI-related annotations", JANDEX_INDEX_PATH);
                return;
            }
        LOGGER.log(Level.FINE, "Using Jandex index at {0}", JANDEX_INDEX_PATH);
        IndexReader ir = new IndexReader(jandexIS);
        OpenApiDocument.INSTANCE.modelFromAnnotations(OpenApiProcessor.modelFromAnnotations(config, ir.read()));
        }
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    private OpenApiStaticFile buildOpenAPIStaticFile() {

        return staticFilePath.isPresent() ? getExplicitStaticFile() : getDefaultStaticFile();
    }

    private OpenApiStaticFile getExplicitStaticFile() {
        Path path = Paths.get(staticFilePath.get());
        final String specifiedFileType = typeFromPath(path);
        final OpenAPIMediaTypes specifiedMediaType = OpenAPIMediaTypes.byFileType(specifiedFileType);

        if (specifiedMediaType == null) {
            throw new IllegalArgumentException("OpenAPI file path "
                    + path.toAbsolutePath().toString()
                    + " is not one of recognized types: "
                    + OpenAPIMediaTypes.recognizedFileTypes());
        }
        final InputStream is;
        try {
            is = Files.newInputStream(path);
        } catch (IOException ex) {
            throw new IllegalArgumentException("OpenAPI file "
                    + path.toAbsolutePath().toString()
                    + " was specified but was not found");
        }

        try {
            LOGGER.log(Level.FINE,
               () ->  String.format(
                       OPENAPI_EXPLICIT_STATIC_FILE_LOG_MESSAGE_FORMAT,
                       path.toAbsolutePath().toString()));
            return new OpenApiStaticFile(is, specifiedMediaType.format());
        } catch (Throwable th) {
            try {
                is.close();
            } catch (IOException ex) {
                LOGGER.log(Level.FINE,
                        "Encountered an error closing an input stream "
                        + "after catching an unrelated error", ex);
            }
            throw th;
        }
    }

    private String typeFromPath(Path path) {
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

    private OpenApiStaticFile getDefaultStaticFile() {
        final List<String> candidatePaths = LOGGER.isLoggable(Level.FINER) ? new ArrayList<>() : null;
        for (OpenAPIMediaTypes candidate : OpenAPIMediaTypes.values()) {
            for (String type : candidate.matchingTypes()) {
                String candidatePath = DEFAULT_STATIC_FILE_PATH_PREFIX + type;
                InputStream is = null;
                try {
                    is = getClass().getResourceAsStream(candidatePath);
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
                } catch (Throwable th) {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ex) {
                            LOGGER.log(Level.FINE,
                                    "Encountered an error closing an input stream "
                                    + "after catching an unrelated error", ex);
                        }
                    }
                    throw th;
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

    private void prepareResponse(ServerRequest req, ServerResponse resp) {

        try {
            final MediaType resultMediaType = chooseResponseMediaType(req);
            final String openAPIDocument = prepareDocument(resultMediaType);
            resp.status(Http.Status.OK_200);
            resp.headers().add(Http.Header.CONTENT_TYPE, resultMediaType.toString());
            resp.send(openAPIDocument);
        } catch (IOException ex) {
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
        if (!OpenApiDocument.INSTANCE.isSet()) {
            throw new IllegalStateException("OpenApiDocument used but has not been initialized");
        }
        synchronized (cachedDocuments) {
            String result = cachedDocuments.get(resultMediaType);
            if (result == null) {
                final Format resultFormat = OpenAPIMediaTypes.byMediaType(resultMediaType).format();
                result = OpenApiSerializer.serialize(
                        OpenApiDocument.INSTANCE.get(), resultFormat);
                cachedDocuments.put(resultMediaType, result);
                LOGGER.log(Level.FINER,
                        "Created and cached OpenAPI document in {0} format",
                        resultFormat.toString());
            } else {
                LOGGER.log(Level.FINER,
                        "Using previously-cached OpenAPI document in {0} format",
                        OpenAPIMediaTypes.DEFAULT_TYPE.toString());
            }
            return result;
        }
    }

    private MediaType chooseResponseMediaType(ServerRequest req) {
        /*
         * Response media type default is application/vnd.oai.openapi (YAML)
         * unless otherwise specified.
         */
        MediaType resultMediaType = DEFAULT_RESPONSE_MEDIA_TYPE;
        Optional<MediaType> requestedMT = req.headers()
                .bestAccepted(OpenAPIMediaTypes.preferredOrdering());
        if (requestedMT.isPresent()) {
            resultMediaType = requestedMT.get();
        }

        return resultMediaType;
    }

    /**
     * Abstraction of the different representations of a static OpenAPI
     * document file and the file type(s) they correspond to.
     * <p>
     * Each {@code OpenAPIMediaType} stands for a single format (e.g., yaml, json).
     * That said, each can map to multiple file types (e.g.,
     * yml and yaml) and multiple actual media types (the proposed OpenAPI media
     * type vnd.oai.openapi and application/x-yaml).
     */
    private enum OpenAPIMediaTypes {

        JSON(Format.JSON,
            new MediaType[] {MediaType.APPLICATION_OPENAPI_JSON, MediaType.APPLICATION_JSON},
            "json"),
        YAML(Format.YAML,
            new MediaType[] {MediaType.APPLICATION_OPENAPI_YAML, MediaType.APPLICATION_YAML},
            "yaml", "yml");

        private static final OpenAPIMediaTypes DEFAULT_TYPE = YAML;

        private final Format format;
        private final List<String> fileTypes;
        private final List<MediaType> mediaTypes;

        OpenAPIMediaTypes(Format format, MediaType[] mediaTypes, String... fileTypes) {
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

        private static OpenAPIMediaTypes byFileType(String fileType) {
            for (OpenAPIMediaTypes candidateType : values()) {
                if (candidateType.matchingTypes().contains(fileType)) {
                    return candidateType;
                }
            }
            return null;
        }

        private static OpenAPIMediaTypes byMediaType(MediaType mt) {
            for (OpenAPIMediaTypes candidateType : values()) {
                if (candidateType.mediaTypes.contains(mt)) {
                    return candidateType;
                }
            }
            return null;
        }

        private static List<String> recognizedFileTypes() {
            final List<String> result = new ArrayList<>();
            for (OpenAPIMediaTypes type : values()) {
                result.addAll(type.fileTypes);
            }
            return result;
        }

        /**
         * Media types we recognize as OpenAPI, in order of preference.
         *
         * @return MediaTypes in order that we recognize them as OpenAPI content.
         */
        private static MediaType[] preferredOrdering() {
            return new MediaType[] {
                MediaType.APPLICATION_OPENAPI_YAML,
                MediaType.APPLICATION_YAML,
                MediaType.APPLICATION_OPENAPI_JSON,
                MediaType.APPLICATION_JSON
            };
        }
    }

    /**
     * Creates a new {@link Builder} for {@code OpenAPISupport} using defaults.
     *
     * @return new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new {@link OpenAPISupport} instance using defaults.
     *
     * @return new OpenAPISUpport
     */
    public static OpenAPISupport create() {
        return builder().build();
    }

    /**
     * Creates a new {@link OpenAPISupport} instance using the
     * '{@value Builder#CONFIG_PREFIX}' portion of the provided {@link Config} object.
     *
     * @param config {@code Config} object containing OpenAPI-related settings
     * @return new {@code OpenAPISupport} instance created using the config settings
     */
    public static OpenAPISupport create(final Config config) {
        return builder().config(config).build();
    }

    /**
     * Fluent API builder for {@link OpenAPISupport}.
     * <p>
     * The builder mostly delegates to an instance of {@link OpenAPIConfigImpl.Builder}
     * which in turn prepares a smallrye {@link OpenApiConfig} which is what the
     * smallrye implementation uses to control its behavior.
     * <p>
     * If the app invokes both the {@link #config} method and other methods for
     * setting individual attributes, the latest builder method invoked "wins" in
     * case of conflicts.
     */
    public static final class Builder implements io.helidon.common.Builder<OpenAPISupport> {

        private static final String CONFIG_PREFIX = "openapi";

        private final OpenAPIConfigImpl.Builder apiConfigBuilder = OpenAPIConfigImpl.builder();

        private Optional<String> webContext = Optional.empty();
        private Optional<String> staticFilePath = Optional.empty();
        private boolean isAnnotationProcessingEnabled = false;

        private Builder() {
        }

        @Override
        public OpenAPISupport build() {
            return new OpenAPISupport(this);
        }

        /**
         * Set various builder attributes from the specified {@code Config} object.
         * <p>
         * The {@code Config} object can specify {@value #CONFIG_PREFIX}.web-context
         * and {@value #CONFIG_PREFIX}.static-file in addition to settings
         * supported by {@link OpenAPIConfigImpl.Builder}.
         *
         * @param config the {@code Config} object possibly containing settings
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get(CONFIG_PREFIX + ".web-context").asString().ifPresent(this::webContext);
            config.get(CONFIG_PREFIX + ".static-file").asString().ifPresent(this::staticFile);

            apiConfigBuilder.config(config);

            return this;
        }

        /**
         * Path under which to register OpenAPI endpoint on the web server.
         *
         * @param path webContext to use, defaults to {@value DEFAULT_WEB_CONTEXT}
         * @return updated builder instance
         */
        public Builder webContext(String path) {
            this.webContext = Optional.of(path);
            return this;
        }

        /**
         * Sets the location of the static OpenAPI document file.
         *
         * @param path location of the static OpenAPI document file
         * @return updated builder instance
         */
        public Builder staticFile(String path) {
            Objects.requireNonNull(path, "path to static file must be non-null");
            staticFilePath = Optional.of(path);
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
         * @param operationID operation ID to which the server corresponds
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
         * @param path path to which the server corresponds
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
         * Sets whether annotation processing for OpenAPI annotations should
         * be enabled.
         *
         * @param isEnabled whether annotation processing should be turned on
         * @return updated builder instance
         */
        public Builder enableAnnotationProcessing(boolean isEnabled) {
            isAnnotationProcessingEnabled = isEnabled;
            return this;
        }
    }
}
