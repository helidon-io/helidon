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
import io.smallrye.openapi.runtime.scanner.FilteredIndexView;
import org.jboss.jandex.IndexView;

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
 * <td>sets the static OpenAPI document file (defaults in order to
 * {@code openapi.yaml}, {@code openapi.yml}, or {@code openapi.json})</td>
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
 * <td>sets the application-provided class for adding to the OpenAPI
 * document</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#filter}</td>
 * <td>sets the application-provided class for filtering OpenAPI document
 * information</td>
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
 * If the application uses none of these builder methods and does not provide a
 * static {@code openapi} file, then the {@code /openapi} endpoint responds with
 * a nearly-empty OpenAPI document.
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

    private final String webContext;
    private final Builder builder;
    private final OpenApiConfig openAPIConfig;

    private final Map<MediaType, String> cachedDocuments = new HashMap<>();

    private OpenAPISupport(final Builder builder) {
        webContext = builder.webContext();
        this.builder = builder;
        this.openAPIConfig = builder.openAPIConfig();
    }

    @Override
    public void update(final Routing.Rules rules) {
        configureEndpoint(rules);
    }

    /**
     * Sets up the OpenAPI endpoint by adding routing to the specified rules
     * set.
     *
     * @param rules routing rules to be augmented with OpenAPI endpoint
     */
    public void configureEndpoint(Routing.Rules rules) {
        try {
            initializeOpenAPIDocument(openAPIConfig);
        } catch (IOException ex) {
            throw new RuntimeException("Error initializing OpenAPI information", ex);
        }

        rules.get(JsonSupport.create())
                .get(webContext, this::prepareResponse);
    }

    /**
     * Prepares the information used to create the OpenAPI document for
     * endpoints in this application.
     *
     * @param config {@code OpenApiConfig} object describing paths, servers,
     * etc.
     * @throws IOException in case of errors reading any existing static OpenAPI
     * document
     */
    void initializeOpenAPIDocument(final OpenApiConfig config) throws IOException {
        try (OpenApiStaticFile staticFile = buildOpenAPIStaticFile()) {
            OpenApiDocument.INSTANCE.reset();
            OpenApiDocument.INSTANCE.config(config);
            OpenApiDocument.INSTANCE.modelFromReader(OpenApiProcessor.modelFromReader(config, getContextClassLoader()));
            OpenApiDocument.INSTANCE.modelFromStaticFile(OpenApiProcessor.modelFromStaticFile(staticFile));
            if (isAnnotationProcessingEnabled(config)) {
                expandModelUsingAnnotations(config);
            } else {
                LOGGER.log(Level.FINE, "OpenAPI Annotation processing is disabled");
            }
            OpenApiDocument.INSTANCE.filter(OpenApiProcessor.getFilter(config, getContextClassLoader()));
            OpenApiDocument.INSTANCE.initialize();
        }
    }

    private boolean isAnnotationProcessingEnabled(OpenApiConfig config) {
        return !config.scanDisable();
    }

    private void expandModelUsingAnnotations(final OpenApiConfig config) throws IOException {
        if (builder.indexView() != null) {
            OpenApiDocument.INSTANCE.modelFromAnnotations(
                    OpenApiProcessor.modelFromAnnotations(config, new FilteredIndexView(builder.indexView(), config)));
        }
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    private OpenApiStaticFile buildOpenAPIStaticFile() {
        return builder.staticFile();
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
        MediaType resultMediaType = req.headers()
                .bestAccepted(OpenAPIMediaTypes.preferredOrdering())
                .orElse(DEFAULT_RESPONSE_MEDIA_TYPE);
        return resultMediaType;
    }

    /**
     * Abstraction of the different representations of a static OpenAPI document
     * file and the file type(s) they correspond to.
     * <p>
     * Each {@code OpenAPIMediaType} stands for a single format (e.g., yaml,
     * json). That said, each can map to multiple file types (e.g., yml and
     * yaml) and multiple actual media types (the proposed OpenAPI media type
     * vnd.oai.openapi and application/x-yaml).
     */
    private enum OpenAPIMediaTypes {

        JSON(Format.JSON,
                new MediaType[]{MediaType.APPLICATION_OPENAPI_JSON, MediaType.APPLICATION_JSON},
            "json"),
        YAML(Format.YAML,
                new MediaType[]{MediaType.APPLICATION_OPENAPI_YAML, MediaType.APPLICATION_YAML},
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
         * @return MediaTypes in order that we recognize them as OpenAPI
         * content.
         */
        private static MediaType[] preferredOrdering() {
            return new MediaType[]{
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
     * '{@value Builder#CONFIG_PREFIX}' portion of the provided {@link Config}
     * object.
     *
     * @param config {@code Config} object containing OpenAPI-related settings
     * @return new {@code OpenAPISupport} instance created using the
     * helidonConfig settings
     */
    public static OpenAPISupport create(final Config config) {
        return builderSE().helidonConfig(config).build();
    }

    /**
     * Returns an OpenAPISupport.Builder for Helidon SE environments.
     *
     * @return Helidon SE {@code OpenAPISupport.Builder}
     */
    public static SEOpenAPISupportBuilder builderSE() {
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

        @Override
        public OpenAPISupport build() {
            validate();
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
         * @return the OpenAPI static file instance for the static file if such a
         * file exists, null otherwise
         */
        OpenApiStaticFile staticFile() {
            return staticFilePath.isPresent() ? getExplicitStaticFile() : getDefaultStaticFile();
        }

        /**
         * Returns the smallrye OpenApiConfig instance describing the set-up that
         * will govern the smallrye OpenAPI behavior.
         *
         * @return {@code OpenApiConfig} conveying how OpenAPI should behave
         */
        public abstract OpenApiConfig openAPIConfig();

        /**
         * Returns the Jandex {@link IndexView} containing annotated endpoint classes.
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
                        () -> String.format(
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

        private OpenApiStaticFile getDefaultStaticFile() {
            final List<String> candidatePaths = LOGGER.isLoggable(Level.FINER) ? new ArrayList<>() : null;
            for (OpenAPIMediaTypes candidate : OpenAPIMediaTypes.values()) {
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
    }
}
