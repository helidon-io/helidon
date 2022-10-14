/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;

import io.smallrye.openapi.runtime.io.Format;

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
    JSON(Format.JSON,
         new MediaType[] {MediaTypes.APPLICATION_OPENAPI_JSON,
                 MediaTypes.APPLICATION_JSON},
         "json"),
    /**
     * YAML media type.
     */
    YAML(Format.YAML,
         new MediaType[] {MediaTypes.APPLICATION_OPENAPI_YAML,
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

    private final Format format;
    private final List<String> fileTypes;
    private final List<MediaType> mediaTypes;

    OpenAPIMediaType(Format format, MediaType[] mediaTypes, String... fileTypes) {
        this.format = format;
        this.mediaTypes = Arrays.asList(mediaTypes);
        this.fileTypes = new ArrayList<>(Arrays.asList(fileTypes));
    }

    /**
     * Format associated with this media type.
     * @return format
     */
    public Format format() {
        return format;
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
