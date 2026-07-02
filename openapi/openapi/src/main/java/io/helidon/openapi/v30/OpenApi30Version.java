/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.openapi.v30;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
import io.helidon.common.media.type.MediaType;
import io.helidon.json.JsonObject;
import io.helidon.openapi.OpenApiDocument;
import io.helidon.openapi.OpenApiDocumentContext;
import io.helidon.openapi.OpenApiFormat;
import io.helidon.openapi.spi.OpenApiVersion;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * OpenAPI 3.0 version implementation.
 */
@Api.Preview
public final class OpenApi30Version implements OpenApiVersion,
        RuntimeType.Api<OpenApi30VersionConfig> {
    static final String TYPE = "3.0";
    private static final Pattern VERSION_PATTERN = Pattern.compile(Pattern.quote(TYPE) + "\\.[0-9]+(?:-.+)?");
    private static final DumperOptions YAML_DUMPER_OPTIONS = yamlDumperOptions();

    private final OpenApi30VersionConfig config;

    OpenApi30Version(OpenApi30VersionConfig config) {
        Objects.requireNonNull(config);
        String version = config.version();
        if (!isSupportedVersion(version)) {
            throw new IllegalArgumentException("OpenAPI " + TYPE + " version implementation cannot produce document version "
                                                       + version + ".");
        }
        this.config = config;
    }

    static boolean isSupportedVersion(String version) {
        return VERSION_PATTERN.matcher(version).matches();
    }

    /**
     * Returns a new builder.
     *
     * @return new builder
     */
    public static OpenApi30VersionConfig.Builder builder() {
        return OpenApi30VersionConfig.builder();
    }

    /**
     * Create a new OpenAPI 3.0 version implementation with default configuration.
     *
     * @return new version implementation
     */
    public static OpenApi30Version create() {
        return builder().build();
    }

    /**
     * Create a new OpenAPI 3.0 version implementation with custom configuration.
     *
     * @param consumer configuration consumer
     * @return new version implementation
     */
    public static OpenApi30Version create(Consumer<OpenApi30VersionConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * Create a new OpenAPI 3.0 version implementation from typed configuration.
     *
     * @param config typed configuration
     * @return new version implementation
     */
    public static OpenApi30Version create(OpenApi30VersionConfig config) {
        return new OpenApi30Version(config);
    }

    @Override
    public String version() {
        return config.version();
    }

    @Override
    public OpenApiDocument parse(OpenApiDocumentContext context, String content, MediaType mediaType) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(content);
        Objects.requireNonNull(mediaType);
        if (OpenApiFormat.valueOf(mediaType) == OpenApiFormat.UNSUPPORTED) {
            throw new IllegalStateException("Unsupported static OpenAPI content type: " + mediaType.text());
        }
        Object loaded = new Yaml().load(content);
        if (loaded == null) {
            return OpenApiDocument.builder().build();
        }
        if (loaded instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
            return OpenApi30DocumentMapper.parse(values);
        }
        throw new IllegalStateException("Static OpenAPI content must be a YAML or JSON object.");
    }

    @Override
    public String render(OpenApiDocumentContext context, OpenApiDocument document) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(document);
        JsonObject root = document.toJsonObject();
        if (!root.containsKey("paths")) {
            throw new IllegalStateException("OpenAPI 3.0 document requires a paths field.");
        }
        Map<String, Object> values = OpenApi30DocumentMapper.render(document, config.version());
        return new Yaml(YAML_DUMPER_OPTIONS).dump(values);
    }

    @Override
    public OpenApi30VersionConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return TYPE;
    }

    private static DumperOptions yamlDumperOptions() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setIndent(2);
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return dumperOptions;
    }
}
