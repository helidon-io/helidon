/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.media.type;

import java.util.HashMap;
import java.util.Map;

enum MediaTypeEnum implements MediaType {
    WILDCARD("*", "*", true),
    APPLICATION_XML("application", "xml"),
    APPLICATION_ATOM_XML("application", "atom+xml"),
    APPLICATION_XHTML_XML("application", "xhtml+xml"),
    APPLICATION_SVG_XML("application", "svg+xml"),
    APPLICATION_JSON("application", "json"),
    APPLICATION_JSON_PATCH_JSON("application", "json-patch+json"),
    APPLICATION_STREAM_JSON("application", "stream+json"),
    APPLICATION_FORM_URLENCODED("application", "x-www-form-urlencoded"),
    MULTIPART_FORM_DATA("multipart", "form-data"),
    MULTIPART_BYTERANGES("multipart", "byteranges"),
    APPLICATION_OCTET_STREAM("application", "octet-stream"),
    TEXT_PLAIN("text", "plain"),
    TEXT_XML("text", "xml"),
    TEXT_HTML("text", "html"),
    APPLICATION_OPENMETRICS_TEXT("application", "openmetrics-text"),
    APPLICATION_OPENAPI_YAML("application", "vnd.oai.openapi"),
    APPLICATION_OPENAPI_JSON("application", "vnd.oai.openapi+json"),
    APPLICATION_X_YAML("application", "x-yaml"),
    APPLICATION_YAML("application", "yaml"),
    TEXT_X_YAML("text", "x-yaml"),
    TEXT_YAML("text", "yaml"),
    APPLICATION_JAVASCRIPT("application", "javascript"),
    TEXT_EVENT_STREAM("text", "event-stream"),
    APPLICATION_X_NDJSON("application", "x-ndjson"),
    APPLICATION_HOCON("application", "hocon");

    private static final Map<String, MediaTypeEnum> BY_FULL_TYPE;

    static {
        Map<String, MediaTypeEnum> byFullType = new HashMap<>();

        for (MediaTypeEnum value : MediaTypeEnum.values()) {
            byFullType.put(value.text(), value);
        }

        BY_FULL_TYPE = Map.copyOf(byFullType);
    }

    private final String type;
    private final String subtype;
    private final String fullType;
    private final boolean wildcard;

    MediaTypeEnum(String type, String subtype) {
        this(type, subtype, false);
    }

    MediaTypeEnum(String type, String subtype, boolean wildcard) {
        this.type = type;
        this.subtype = subtype;
        this.fullType = type + "/" + subtype;
        this.wildcard = wildcard;
    }

    static MediaTypeEnum find(String fullType) {
        return BY_FULL_TYPE.get(fullType);
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String subtype() {
        return subtype;
    }

    @Override
    public String text() {
        return fullType;
    }

    @Override
    public boolean isWildcardType() {
        return wildcard;
    }

    @Override
    public boolean isWildcardSubtype() {
        return wildcard;
    }
}
