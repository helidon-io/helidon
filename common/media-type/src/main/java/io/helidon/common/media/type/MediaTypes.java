/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Media type detection based on a resource.
 * <p>The media type detection uses the following algorithm:
 * <ul>
 *     <li>Queries {@link io.helidon.common.media.type.spi.MediaTypeDetector} services in priority order</li>
 *     <li>Checks all {@code META-INF/media-types.properties} files on classpath for a mapping (suffix=media type)</li>
 *     <li>Checks built-in mapping provided by Helidon (with the usual the web relevant media types)</li>
 * </ul>
 */
public final class MediaTypes {
    /**
     * Wildcard media type.
     */
    public static final MediaType WILDCARD = MediaTypeEnum.WILDCARD;
    /**
     * {@code application/xml} media type.
     */
    public static final MediaType APPLICATION_XML = MediaTypeEnum.APPLICATION_XML;
    /**
     * {@code application/atom+xml} media type.
     */
    public static final MediaType APPLICATION_ATOM_XML = MediaTypeEnum.APPLICATION_ATOM_XML;
    /**
     * {@code application/xhtml+xml} media type.
     */
    public static final MediaType APPLICATION_XHTML_XML = MediaTypeEnum.APPLICATION_XHTML_XML;
    /**
     * {@code application/svg+xml} media type.
     */
    public static final MediaType APPLICATION_SVG_XML = MediaTypeEnum.APPLICATION_SVG_XML;
    /**
     * {@code application/json} media type.
     */
    public static final MediaType APPLICATION_JSON = MediaTypeEnum.APPLICATION_JSON;
    /**
     * {@code application/json-patch+json} media type.
     */
    public static final MediaType APPLICATION_JSON_PATCH_JSON = MediaTypeEnum.APPLICATION_JSON_PATCH_JSON;
    /**
     * {@code application/stream+json} media type.
     */
    public static final MediaType APPLICATION_STREAM_JSON = MediaTypeEnum.APPLICATION_STREAM_JSON;
    /**
     * {@code application/x-www-form-urlencoded} media type.
     */
    public static final MediaType APPLICATION_FORM_URLENCODED = MediaTypeEnum.APPLICATION_FORM_URLENCODED;
    /**
     * {@code multipart/form-data} media type.
     */
    public static final MediaType MULTIPART_FORM_DATA = MediaTypeEnum.MULTIPART_FORM_DATA;
    /**
     * {@code multipart/byte-ranges} media type.
     */
    public static final MediaType MULTIPART_BYTERANGES = MediaTypeEnum.MULTIPART_BYTERANGES;
    /**
     * {@code application/octet-stream} media type.
     */
    public static final MediaType APPLICATION_OCTET_STREAM = MediaTypeEnum.APPLICATION_OCTET_STREAM;
    /**
     * {@code tet/plain} media type.
     */
    public static final MediaType TEXT_PLAIN = MediaTypeEnum.TEXT_PLAIN;
    /**
     * {@code text/xml} media type.
     */
    public static final MediaType TEXT_XML = MediaTypeEnum.TEXT_XML;
    /**
     * {@code text/html} media type.
     */
    public static final MediaType TEXT_HTML = MediaTypeEnum.TEXT_HTML;
    /**
     * {@code application/vnd.oai.openapi} media type.
     */
    public static final MediaType APPLICATION_OPENAPI_YAML = MediaTypeEnum.APPLICATION_OPENAPI_YAML;
    /**
     * {@code application/vnd.oai.openapi+json} media type.
     */
    public static final MediaType APPLICATION_OPENAPI_JSON = MediaTypeEnum.APPLICATION_OPENAPI_JSON;
    /**
     * {@code application/openmetrics-text} media type.
     */
    public static final MediaType APPLICATION_OPENMETRICS_TEXT = MediaTypeEnum.APPLICATION_OPENMETRICS_TEXT;
    /**
     * {@code application/x-yaml} media type.
     */
    public static final MediaType APPLICATION_X_YAML = MediaTypeEnum.APPLICATION_X_YAML;
    /**
     * {@code application/yaml} media type.
     */
    public static final MediaType APPLICATION_YAML = MediaTypeEnum.APPLICATION_YAML;
    /**
     * {@code text/x-yaml} media type.
     */
    public static final MediaType TEXT_X_YAML = MediaTypeEnum.TEXT_X_YAML;
    /**
     * {@code text/yaml} media type.
     */
    public static final MediaType TEXT_YAML = MediaTypeEnum.TEXT_YAML;
    /**
     * {@code application/javascript} media type.
     */
    public static final MediaType APPLICATION_JAVASCRIPT = MediaTypeEnum.APPLICATION_JAVASCRIPT;
    /**
     * {@code text/event-stream} media type.
     */
    public static final MediaType TEXT_EVENT_STREAM = MediaTypeEnum.TEXT_EVENT_STREAM;
    /**
     * {@code application/x-ndjson} media type.
     */
    public static final MediaType APPLICATION_X_NDJSON = MediaTypeEnum.APPLICATION_X_NDJSON;
    /**
     * {@code application/hocon} media type.
     */
    public static final MediaType APPLICATION_HOCON = MediaTypeEnum.APPLICATION_HOCON;

    /**
     * String value of media type: {@value}.
     */
    public static final String WILDCARD_VALUE = "*/*";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_XML_VALUE = "application/xml";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_ATOM_XML_VALUE = "application/atom+xml";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_XHTML_XML_VALUE = "application/xhtml+xml";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_SVG_XML_VALUE = "application/svg+xml";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_JSON_VALUE = "application/json";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_JSON_PATCH_JSON_VALUE = "application/json-patch+json";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_STREAM_JSON_VALUE = "application/stream+json";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_FORM_URLENCODED_VALUE = "application/x-www-form-urlencoded";
    /**
     * String value of media type: {@value}.
     */
    public static final String MULTIPART_FORM_DATA_VALUE = "multipart/form-data";
    /**
     * String value of media type: {@value}.
     */
    public static final String MULTIPART_BYTERANGES_VALUE = "multipart/byteranges";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_OCTET_STREAM_VALUE = "application/octet-stream";
    /**
     * String value of media type: {@value}.
     */
    public static final String TEXT_PLAIN_VALUE = "text/plain";
    /**
     * String value of media type: {@value}.
     */
    public static final String TEXT_XML_VALUE = "text/xml";
    /**
     * String value of media type: {@value}.
     */
    public static final String TEXT_HTML_VALUE = "text/html";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_OPENMETRICS_TEXT_VALUE = "application/openmetrics-text";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_OPENAPI_YAML_VALUE = "application/vnd.oai.openapi";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_OPENAPI_JSON_VALUE = "application/vnd.oai.openapi+json";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_X_YAML_VALUE = "application/x-yaml";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_YAML_VALUE = "application/yaml";
    /**
     * String value of media type: {@value}.
     */
    public static final String TEXT_X_YAML_VALUE = "text/x-yaml";
    /**
     * String value of media type: {@value}.
     */
    public static final String TEXT_YAML_VALUE = "text/yaml";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_JAVASCRIPT_VALUE = "application/javascript";
    /**
     * String value of media type: {@value}.
     */
    public static final String TEXT_EVENT_STREAM_VALUE = "text/event-stream";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_X_NDJSON_VALUE = "application/x-ndjson";
    /**
     * String value of media type: {@value}.
     */
    public static final String APPLICATION_HOCON_VALUE = "application/hocon";

    // prevent instantiation of utility class
    private MediaTypes() {
    }

    /**
     * Create media type from the type and subtype.
     *
     * @param type    type
     * @param subtype subtype
     * @return media type for the instance
     */
    public static MediaType create(String type, String subtype) {
        MediaTypeEnum mediaTypeEnum = MediaTypeEnum.find(type + "/" + subtype);
        if (mediaTypeEnum == null) {
            return new MediaTypeImpl(type,
                                     subtype,
                                     type + "/" + subtype);
        } else {
            return mediaTypeEnum;
        }
    }

    /**
     * Create a new media type from the full media type string.
     * Strict media type parsing mode is used.
     *
     * @param fullType media type string, such as {@code application/json}
     * @return media type for the string
     */
    public static MediaType create(String fullType) {
        MediaTypeEnum types = MediaTypeEnum.find(fullType);
        return types == null ? MediaTypeImpl.parse(fullType, ParserMode.STRICT) : types;
    }

    /**
     * Create a new media type from the full media type string.
     *
     * @param fullType media type string, such as {@code application/json}
     * @param parserMode media type parsing mode
     * @return media type for the string
     */
    public static MediaType create(String fullType, ParserMode parserMode) {
        MediaTypeEnum types = MediaTypeEnum.find(fullType);
        return types == null ? MediaTypeImpl.parse(fullType, parserMode) : types;
    }

    /**
     * Detect media type based on URL.
     * As there may be an infinite number of urls used in a system, the results are NOT cached.
     *
     * @param url to determine media type for
     * @return media type or empty if none found
     */
    public static Optional<MediaType> detectType(URL url) {
        return Detectors.detectType(url);
    }

    /**
     * Detect media type based on URI.
     * Results may not be cached.
     *
     * @param uri to determine media type for
     * @return media type or empty if none found
     */
    public static Optional<MediaType> detectType(URI uri) {
        return Detectors.detectType(uri);
    }

    /**
     * Detect media type for a file on file system.
     * Results may not be cached.
     *
     * @param file file on a file system
     * @return media type or empty if none found
     */
    public static Optional<MediaType> detectType(Path file) {
        return Detectors.detectType(file);
    }

    /**
     * Detect media type for a path (may be URL, URI, path on a file system).
     * Results may not be cached. If you have {@link java.net.URL}, {@link java.net.URI}, or {@link java.nio.file.Path} please
     * use the other methods on this class.
     *
     * @param fileName any string that has a file name as its last element
     * @return media type or empty if none found
     * @see #detectType(java.net.URI)
     * @see #detectType(java.net.URL)
     * @see #detectType(java.nio.file.Path)
     */
    public static Optional<MediaType> detectType(String fileName) {
        return Detectors.detectType(fileName);
    }

    /**
     * Detecd media type for a specific file extension.
     * Results are cached.
     *
     * @param fileSuffix suffix of a file, such as {@code txt}, {@code properties}, or {@code jpeg}. Without the leading dot.
     * @return media type for the file suffix or empty if none found
     */
    public static Optional<MediaType> detectExtensionType(String fileSuffix) {
        return Detectors.detectExtensionType(fileSuffix);
    }
}
