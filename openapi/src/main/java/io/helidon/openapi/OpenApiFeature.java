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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.servicecommon.HelidonFeatureSupport;

/**
 * Behavior shared between the SE and MP OpenAPI feature implementations.
 */
public abstract class OpenApiFeature extends HelidonFeatureSupport {

    /**
     * Default media type used in responses in absence of incoming Accept
     * header.
     */
    public static final MediaType DEFAULT_RESPONSE_MEDIA_TYPE = MediaTypes.APPLICATION_OPENAPI_YAML;

    /**
     * Feature name for OpenAPI.
     */
    public static final String FEATURE_NAME = "OpenAPI";

    /**
     * Default web context for the endpoint.
     */
    public static final String DEFAULT_CONTEXT = "/openapi";

    /**
     * Returns a new builder for preparing an SE variant of {@code OpenApiFeature}.
     *
     * @return new builder
     */
    public static Builder<?, ?> builder() {
        return new SeOpenApiFeature.Builder();
    }

    /**
     * Create a new instance of an Open API feature from configuration.
     *
     * @param config configuration to use
     * @return a new Open API feature
     */
    public static OpenApiFeature create(io.helidon.common.config.Config config) {
        return builder().config(config).build();
    }

    /**
     * URL query parameter for specifying the requested format when retrieving the OpenAPI document.
     */
    static final String OPENAPI_ENDPOINT_FORMAT_QUERY_PARAMETER = "format";

    /**
     * Abstraction of the different representations of a static OpenAPI document
     * file and the file type(s) they correspond to.
     * <p>
     * Each {@code OpenAPIMediaType} stands for a single format (e.g., yaml,
     * json). That said, each can map to multiple file types (e.g., yml and
     * yaml) and multiple actual media types (the proposed OpenAPI media type
     * vnd.oai.openapi and various other YAML types proposed or in use).
     */
    public enum OpenAPIMediaType {
        /**
         * JSON media type.
         */
        JSON(new MediaType[] {MediaTypes.APPLICATION_OPENAPI_JSON,
                MediaTypes.APPLICATION_JSON},
             "json"),
        /**
         * YAML media type.
         */
        YAML(new MediaType[] {MediaTypes.APPLICATION_OPENAPI_YAML,
                MediaTypes.APPLICATION_X_YAML,
                MediaTypes.APPLICATION_YAML,
                MediaTypes.TEXT_PLAIN,
                MediaTypes.TEXT_X_YAML,
                MediaTypes.TEXT_YAML},
             "yaml", "yml");

        /**
         * Default media type (YAML).
         */
        public static final OpenAPIMediaType DEFAULT_TYPE = YAML;

        static final String TYPE_LIST = "json|yaml|yml"; // must be a true constant so it can be used in an annotation

        private final List<String> fileTypes;
        private final List<MediaType> mediaTypes;

        OpenAPIMediaType(MediaType[] mediaTypes, String... fileTypes) {
            this.mediaTypes = Arrays.asList(mediaTypes);
            this.fileTypes = new ArrayList<>(Arrays.asList(fileTypes));
        }

        /**
         * File types matching this media type.
         * @return file types
         */
        public List<String> matchingTypes() {
            return fileTypes;
        }

        /**
         * Find media type by file suffix.
         *
         * @param fileType file suffix
         * @return media type or empty if not supported
         */
        public static Optional<OpenAPIMediaType> byFileType(String fileType) {
            for (OpenAPIMediaType candidateType : values()) {
                if (candidateType.matchingTypes().contains(fileType)) {
                    return Optional.of(candidateType);
                }
            }
            return Optional.empty();
        }

        /**
         * Find OpenAPI media type by media type.
         * @param mt media type
         * @return OpenAPI media type or empty if not supported
         */
        public static Optional<OpenAPIMediaType> byMediaType(MediaType mt) {
            for (OpenAPIMediaType candidateType : values()) {
                if (candidateType.mediaTypes.contains(mt)) {
                    return Optional.of(candidateType);
                }
            }
            return Optional.empty();
        }

        /**
         * List of all supported file types.
         *
         * @return file types
         */
        public static List<String> recognizedFileTypes() {
            final List<String> result = new ArrayList<>();
            for (OpenAPIMediaType type : values()) {
                result.addAll(type.fileTypes);
            }
            return result;
        }

        /**
         * Media types we recognize as OpenAPI, in order of preference.
         *
         * @return MediaTypes in order that we recognize them as OpenAPI
         *         content.
         */
        public static MediaType[] preferredOrdering() {
            return new MediaType[] {
                    MediaTypes.APPLICATION_OPENAPI_YAML,
                    MediaTypes.APPLICATION_X_YAML,
                    MediaTypes.APPLICATION_YAML,
                    MediaTypes.APPLICATION_OPENAPI_JSON,
                    MediaTypes.APPLICATION_JSON,
                    MediaTypes.TEXT_X_YAML,
                    MediaTypes.TEXT_YAML,
                    MediaTypes.TEXT_PLAIN
            };
        }
    }

    /**
     * Some logic related to the possible format values as requested in the query
     * parameter {@value OPENAPI_ENDPOINT_FORMAT_QUERY_PARAMETER}.
     */
    enum QueryParameterRequestedFormat {
        JSON(MediaTypes.APPLICATION_JSON), YAML(MediaTypes.APPLICATION_OPENAPI_YAML);

        static QueryParameterRequestedFormat chooseFormat(String format) {
            return QueryParameterRequestedFormat.valueOf(format);
        }

        private final MediaType mt;

        QueryParameterRequestedFormat(MediaType mt) {
            this.mt = mt;
        }

        MediaType mediaType() {
            return mt;
        }
    }

    private static final String DEFAULT_STATIC_FILE_PATH_PREFIX = "META-INF/openapi.";
    private static final String OPENAPI_EXPLICIT_STATIC_FILE_LOG_MESSAGE_FORMAT = "Using specified OpenAPI static file %s";
    private static final String OPENAPI_DEFAULTED_STATIC_FILE_LOG_MESSAGE_FORMAT = "Using default OpenAPI static file %s";

    private final OpenApiStaticFile openApiStaticFile;
    private final OpenApiUi ui;
    private final MediaType[] preferredMediaTypeOrdering;
    private final MediaType[] mediaTypesSupportedByUi;
    private final ConcurrentMap<OpenAPIMediaType, String> cachedDocuments = new ConcurrentHashMap<>();

    /**
     * Constructor for the feature.
     *
     * @param logger logger to use for the feature
     * @param builder builder to use for initializing the feature
     */
    protected OpenApiFeature(System.Logger logger, Builder<?, ?> builder) {
        super(logger, builder, FEATURE_NAME);
        openApiStaticFile = builder.staticFile();
        ui = prepareUi(builder);
        mediaTypesSupportedByUi = ui.supportedMediaTypes();
        preferredMediaTypeOrdering = preparePreferredMediaTypeOrdering(mediaTypesSupportedByUi);
    }

    @Override
    public Optional<HttpService> service() {
        return enabled()
                ? Optional.of(this::configureRoutes)
                : Optional.empty();
    }

    /**
     * Returns the OpenAPI document content in {@code String} form given the requested media type.
     *
     * @param openApiMediaType which OpenAPI media type to use for formatting
     * @return {@code String} containing the formatted OpenAPI document
     */
    protected abstract String openApiContent(OpenAPIMediaType openApiMediaType);

    /**
     * Returns the explicitly-assigned or default static content (if any).
     * <p>
     *     Most likely invoked by the concrete implementations of {@link #openApiContent(OpenAPIMediaType)} as needed
     *     to find static content as needed.
     * </p>
     *
     * @return an {@code Optional} of the static content
     */
    protected Optional<OpenApiStaticFile> staticContent() {
        return Optional.ofNullable(openApiStaticFile);
    }

    private OpenApiUi prepareUi(Builder<?, ?> builder) {
        return builder.uiBuilder.build(this::prepareDocument, context());
    }

    private static MediaType[] preparePreferredMediaTypeOrdering(MediaType[] uiTypesSupported) {
        int nonTextLength = OpenAPIMediaType.preferredOrdering().length;

        MediaType[] result = Arrays.copyOf(OpenAPIMediaType.preferredOrdering(),
                                           nonTextLength + uiTypesSupported.length);
        System.arraycopy(uiTypesSupported, 0, result, nonTextLength, uiTypesSupported.length);
        return result;
    }

    private void configureRoutes(HttpRules rules) {
        rules.get("/", this::prepareResponse);
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    private static String typeFromPath(String staticFileNamePath) {
        if (staticFileNamePath == null) {
            throw new IllegalArgumentException("File path does not seem to have a file name value but one is expected");
        }
        return staticFileNamePath.substring(staticFileNamePath.lastIndexOf(".") + 1);
    }

    private void prepareResponse(ServerRequest req, ServerResponse resp) {

        try {
            Optional<MediaType> requestedMediaType = chooseResponseMediaType(req);

            // Give the UI a chance to respond first if it claims to support the chosen media type.
            if (requestedMediaType.isPresent()
                    && uiSupportsMediaType(requestedMediaType.get())) {
                if (ui.prepareTextResponseFromMainEndpoint(req, resp)) {
                    return;
                }
            }

            if (requestedMediaType.isEmpty()) {
                logger().log(System.Logger.Level.TRACE,
                           () -> String.format("Did not recognize requested media type %s; passing the request on",
                                               req.headers().acceptedTypes()));
                return;
            }

            MediaType resultMediaType = requestedMediaType.get();
            final String openAPIDocument = prepareDocument(resultMediaType);
            resp.status(Status.OK_200);
            resp.headers().contentType(resultMediaType);
            resp.send(openAPIDocument);
        } catch (Exception ex) {
            resp.status(Status.INTERNAL_SERVER_ERROR_500);
            resp.send("Error serializing OpenAPI document; " + ex.getMessage());
            logger().log(System.Logger.Level.ERROR, "Error serializing OpenAPI document", ex);
        }
    }

    private boolean uiSupportsMediaType(MediaType mediaType) {
        HttpMediaType httpMediaType = HttpMediaType.create(mediaType);
        // The UI supports a very short list of media types, hence the sequential search.
        for (MediaType uiSupportedMediaType : mediaTypesSupportedByUi) {
            if (httpMediaType.test(uiSupportedMediaType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the OpenAPI document in the requested format.
     *
     * @param resultMediaType requested media type
     * @return String containing the formatted OpenAPI document
     * from its underlying data
     */
    private String prepareDocument(MediaType resultMediaType) {
        OpenAPIMediaType matchingOpenApiMediaType
                = OpenAPIMediaType.byMediaType(resultMediaType)
                .orElseGet(() -> {
                    logger().log(System.Logger.Level.TRACE,
                                 () -> String.format(
                                       "Requested media type %s not supported; using default",
                                       resultMediaType.toString()));
                    return OpenAPIMediaType.DEFAULT_TYPE;
                });


        return cachedDocuments.computeIfAbsent(matchingOpenApiMediaType,
                                                        fmt -> {
                                                            String r = openApiContent(fmt);
                                                            logger().log(System.Logger.Level.TRACE,
                                                                       "Created and cached OpenAPI document in {0} format",
                                                                       fmt.toString());
                                                            return r;
                                                        });
    }

    private Optional<MediaType> chooseResponseMediaType(ServerRequest req) {
        /*
         * Response media type default is application/vnd.oai.openapi (YAML)
         * unless otherwise specified.
         */
        OptionalValue<String> queryParameterFormat = req.query()
                .first(OPENAPI_ENDPOINT_FORMAT_QUERY_PARAMETER);
        if (queryParameterFormat.isPresent()) {
            String queryParameterFormatValue = queryParameterFormat.get();
            try {
                return Optional.of(QueryParameterRequestedFormat.chooseFormat(queryParameterFormatValue).mediaType());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Query parameter 'format' had value '"
                                + queryParameterFormatValue
                                + "' but expected " + Arrays.toString(QueryParameterRequestedFormat.values()));
            }
        }

        ServerRequestHeaders headersToCheck = req.headers();
        if (headersToCheck.acceptedTypes().isEmpty()) {
            WritableHeaders<?> writableHeaders = WritableHeaders.create(headersToCheck);
            writableHeaders.add(HeaderNames.ACCEPT, DEFAULT_RESPONSE_MEDIA_TYPE.toString());
            headersToCheck = ServerRequestHeaders.create(writableHeaders);
        }
        return headersToCheck
                .bestAccepted(preferredMediaTypeOrdering);
    }


    /**
     * Behavior shared between the SE and MP OpenAPI feature builders.
     *
     * @param <B> specific concrete type of the builder
     * @param <T> specific concrete type of {@link OpenApiFeature} the builder creates
     */
    public abstract static class Builder<B extends Builder<B, T>, T extends OpenApiFeature>
            extends HelidonFeatureSupport.Builder<B, T> {

        /**
         * Config key for the OpenAPI section.
         */
        public static final String CONFIG_KEY = "openapi";

        private OpenApiStaticFile staticFile;

        private OpenApiUi.Builder<?, ?> uiBuilder = OpenApiUi.builder();

        /**
         * Constructor for the builder.
         */
        protected Builder() {
            super(DEFAULT_CONTEXT);
        }

        /**
         * Returns the logger for the OpenAPI feature instance.
         *
         * @return logger
         */
        protected abstract System.Logger logger();

        /**
         * Apply configuration settings to the builder.
         *
         * @param config the Helidon config instance
         * @return updated builder
         */
        public B config(Config config) {
            super.config(config);
            config.get("static-file")
                    .asString()
                    .ifPresent(this::staticFile);
            config.get(OpenApiUi.Builder.OPENAPI_UI_CONFIG_KEY)
                    .ifExists(uiBuilder::config);
            return identity();
        }

        /**
         * Sets the path of the static OpenAPI document file. Default types are `json`, `yaml`, and `yml`.
         *
         * @param path non-null location of the static OpenAPI document file
         * @return updated builder instance
         */
        @ConfiguredOption(value = DEFAULT_STATIC_FILE_PATH_PREFIX + "*")
        public B staticFile(String path) {
            Objects.requireNonNull(path, "path to static file must be non-null");
            OpenAPIMediaType openApiMediaType = OpenAPIMediaType.byFileType(typeFromPath(path))
                    .orElseThrow(() -> new IllegalArgumentException("Static file " + path + " not recognized as YAML or JSON"));

            staticFile = OpenApiStaticFile.create(openApiMediaType, explicitStaticFileContentFromPath(path));

            return identity();
        }

        /**
         * Assigns the OpenAPI UI builder the {@code OpenAPISupport} service should use in preparing the UI.
         *
         * @param uiBuilder the {@link OpenApiUi.Builder}
         * @return updated builder instance
         */
        @ConfiguredOption(type = OpenApiUi.class)
        public B ui(OpenApiUi.Builder<?, ?> uiBuilder) {
            Objects.requireNonNull(uiBuilder, "UI must be non-null");
            this.uiBuilder = uiBuilder;
            return identity();
        }

        /**
         * Returns the path to a static OpenAPI document file (if any exists),
         * either as explicitly set using {@link #staticFile(java.lang.String) }
         * or one of the default files.
         *
         * @return the OpenAPI static file instance for the static file if such
         * a file exists, null otherwise
         */
        public OpenApiStaticFile staticFile() {
            return staticFile == null
                    ? getDefaultStaticFile()
                    : staticFile;
        }

        private OpenApiStaticFile getDefaultStaticFile() {
            OpenApiStaticFile result = null;
            final List<String> candidatePaths = logger().isLoggable(System.Logger.Level.TRACE) ? new ArrayList<>() : null;
            for (OpenAPIMediaType candidate : OpenAPIMediaType.values()) {
                for (String type : candidate.matchingTypes()) {
                    String candidatePath = DEFAULT_STATIC_FILE_PATH_PREFIX + type;
                    if (candidatePaths != null) {
                        candidatePaths.add(candidatePath);
                    }
                    String content = defaultStaticFileContentFromPath(candidatePath);
                    if (content != null) {
                        result = OpenApiStaticFile.create(candidate, content);
                    }
                }
            }
            if (candidatePaths != null) {
                logger().log(System.Logger.Level.TRACE,
                           candidatePaths.stream()
                                   .collect(Collectors.joining(
                                           ",",
                                           "No default static OpenAPI description file found; checked [",
                                           "]")));
            }
            return result;
        }

        private String defaultStaticFileContentFromPath(String candidatePath) {
            return staticFileContentFromPath(candidatePath, OPENAPI_DEFAULTED_STATIC_FILE_LOG_MESSAGE_FORMAT);
        }

        private String explicitStaticFileContentFromPath(String candidatePath) {
            return staticFileContentFromPath(candidatePath, OPENAPI_EXPLICIT_STATIC_FILE_LOG_MESSAGE_FORMAT);
        }

        private String staticFileContentFromPath(String candidatePath, String logMessage) {
            InputStream is = getContextClassLoader().getResourceAsStream(candidatePath);
            if (is != null) {
                try (Reader reader = new BufferedReader(new InputStreamReader(is, Charset.defaultCharset()))) {
                    Path path = Paths.get(candidatePath);
                    logger().log(System.Logger.Level.TRACE, () -> String.format(
                            logMessage,
                            path.toAbsolutePath()));
                    StringBuilder result = new StringBuilder();
                    CharBuffer charBuffer = CharBuffer.allocate(512);
                    while (reader.read(charBuffer) != -1) {
                        charBuffer.flip();
                        result.append(charBuffer);
                    }
                    return result.toString();
                } catch (IOException ex) {
                    throw new IllegalArgumentException("Error preparing to read from path " + candidatePath, ex);
                }
            } else {
                return null;
            }
        }
    }
}
