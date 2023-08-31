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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;

/**
 * Supported OpenApi formats.
 */
public enum OpenApiFormat {
    /**
     * JSON.
     */
    JSON(new MediaType[] {
            MediaTypes.APPLICATION_OPENAPI_JSON,
            MediaTypes.APPLICATION_JSON
    }, "json"),

    /**
     * YAML.
     */
    YAML(new MediaType[] {
            MediaTypes.APPLICATION_OPENAPI_YAML,
            MediaTypes.APPLICATION_X_YAML,
            MediaTypes.APPLICATION_YAML,
            MediaTypes.TEXT_PLAIN,
            MediaTypes.TEXT_X_YAML,
            MediaTypes.TEXT_YAML
    }, "yaml", "yml"),

    /**
     * Unsupported format.
     */
    UNSUPPORTED(new MediaType[0]);

    private final List<String> fileTypes;
    private final List<MediaType> mediaTypes;

    OpenApiFormat(MediaType[] mediaTypes, String... fileTypes) {
        this.mediaTypes = Arrays.asList(mediaTypes);
        this.fileTypes = new ArrayList<>(Arrays.asList(fileTypes));
    }

    /**
     * File types usable with this format.
     *
     * @return file types
     */
    public List<String> fileTypes() {
        return fileTypes;
    }

    private boolean supports(MediaType mediaType) {
        for (MediaType mt : mediaTypes) {
            if (mediaType.type().equals(mt.type())
                    && mediaType.subtype().equals(mt.subtype())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find OpenAPI media type by media type.
     *
     * @param mediaType media type
     * @return OpenAPI media type
     */
    public static OpenApiFormat valueOf(MediaType mediaType) {
        for (OpenApiFormat candidateType : values()) {
            if (candidateType.supports(mediaType)) {
                return candidateType;
            }
        }
        return OpenApiFormat.UNSUPPORTED;
    }
}
