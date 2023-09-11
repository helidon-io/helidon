/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.mapper.Value;

/**
 * A generic representation of the {@code Content-Disposition} header.
 * <p>
 * Parameter encoding is not supported, other than
 * <a href="https://tools.ietf.org/html/rfc3986#section-2.1">URI percent
 * encoding</a> in the filename parameter. See {@link java.net.URLDecoder}.
 * </p>
 * See also:
 * <ul>
 * <li><a href="https://tools.ietf.org/html/rfc2183">Communicating Presentation
 * Information in Internet Messages: The Content-Disposition Header Field</a>
 * </li>
 * <li><a href="https://tools.ietf.org/html/rfc7578#section-4.2">Content-Disposition
 * Header Field for each part</a>
 * </li>
 * </ul>
 */
public class ContentDisposition implements Header {
    private static final String NAME_PARAMETER = "name";
    private static final String FILENAME_PARAMETER = "filename";
    private static final String CREATION_DATE_PARAMETER = "creation-date";
    private static final String MODIFICATION_DATE_PARAMETER = "modification-date";
    private static final String READ_DATE_PARAMETER = "read-date";
    private static final String SIZE_PARAMETER = "size";
    private static final ContentDisposition EMPTY = ContentDisposition.builder()
            .type("")
            .build();

    private static final Pattern DISPOSITION_PART_PATTERN = Pattern.compile("^(.+?)=\"?(.+?)\"?$");

    private final String type;
    private final Map<String, String> parameters;

    private String value;

    private ContentDisposition(Builder builder) {
        this.type = builder.type;
        this.parameters = new LinkedHashMap<>(builder.parameters);
    }

    /**
     * A new builder to set up content disposition.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Parse a received header value.
     *
     * @param headerValue content disposition header value
     * @return a parsed content disposition
     */
    public static ContentDisposition parse(String headerValue) {
        Builder builder = ContentDisposition.builder();

        // first split by semicolon
        String[] parts = headerValue.split("(?<=[^\\\\]);");

        if (parts.length > 0) {
            String type = parts[0];
            if (type.indexOf('=') > -1) {
                throw new IllegalArgumentException("No type defined");
            } else {
                builder.type(type.trim());
            }
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i];
                Matcher matcher = DISPOSITION_PART_PATTERN.matcher(part.trim());
                if (matcher.matches()) {
                    String name = matcher.group(1);
                    String value = matcher.group(2);
                    value = value.replace("\\\\", "\\");
                    value = value.replace("\\\"", "\"");
                    value = value.replace("\\;", ";");
                    builder.parameter(name, value);
                }
            }
        }
        return builder.build();
    }

    /**
     * An empty content disposition.
     *
     * @return empty disposition with empty type
     */
    public static ContentDisposition empty() {
        return EMPTY;
    }

    @Override
    public String name() {
        return HeaderNames.CONTENT_DISPOSITION.defaultCase();
    }

    @Override
    public HeaderName headerName() {
        return HeaderNames.CONTENT_DISPOSITION;
    }

    @Override
    public String get() {
        if (value == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(type);
            for (Map.Entry<String, String> param : parameters.entrySet()) {
                sb.append(";");
                sb.append(param.getKey());
                sb.append("=");
                if (SIZE_PARAMETER.equals(param.getKey())) {
                    sb.append(param.getValue());
                } else {
                    sb.append("\"");
                    sb.append(param.getValue());
                    sb.append("\"");
                }
            }
            value = sb.toString();
        }
        return value;
    }

    @Override
    public <N> Value<N> as(Class<N> type) throws MapperException {
        return asString().as(type);
    }

    @Override
    public <N> Value<N> as(GenericType<N> type) throws MapperException {
        return asString().as(type);
    }

    @Override
    public <N> Value<N> as(Function<? super String, ? extends N> mapper) {
        return asString().as(mapper);
    }

    @Override
    public Optional<String> asOptional() throws MapperException {
        return asString().asOptional();
    }

    @Override
    public Value<Boolean> asBoolean() {
        return asString().asBoolean();
    }

    @Override
    public Value<String> asString() {
        return Value.create(MapperManager.global(), name(), get(), GenericType.STRING, "http", "header");
    }

    @Override
    public Value<Integer> asInt() {
        return asString().asInt();
    }

    @Override
    public Value<Long> asLong() {
        return asString().asLong();
    }

    @Override
    public Value<Double> asDouble() {
        return asString().asDouble();
    }

    @Override
    public List<String> allValues() {
        return List.of(get());
    }

    @Override
    public int valueCount() {
        return 1;
    }

    @Override
    public boolean sensitive() {
        return false;
    }

    @Override
    public boolean changing() {
        return true;
    }

    @Override
    public String toString() {
        return get();
    }

    /**
     * Get the value of the {@code name} parameter. In the case of a
     * {@code form-data} disposition type the value is the original field name
     * from the form.
     *
     * @return {@code Optional<String>}, never {@code null}
     */
    public Optional<String> contentName() {
        return Optional.ofNullable(parameters.get(NAME_PARAMETER));
    }

    /**
     * Get the value of the {@code filename} parameter that can be used to
     * suggest a filename to be used if the entity is detached and stored in a
     * separate file.
     *
     * @return {@code Optional<String>}, never {@code null}
     */
    public Optional<String> filename() {
        String filename = null;
        String value = parameters.get(FILENAME_PARAMETER);
        if (value != null) {
            filename = URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return Optional.ofNullable(filename);
    }

    /**
     * Get the value of the {@code creation-date} parameter that can be used
     * to indicate the date at which the file was created.
     *
     * @return {@code Optional<ZonedDateTime>}, never {@code null}
     */
    public Optional<ZonedDateTime> creationDate() {
        return Optional.ofNullable(parameters.get(CREATION_DATE_PARAMETER)).map(DateTime::parse);
    }

    /**
     * Get the value of the {@code modification-date} parameter that can be
     * used to indicate the date at which the file was last modified.
     *
     * @return {@code Optional<ZonedDateTime>}, never {@code null}
     */
    public Optional<ZonedDateTime> modificationDate() {
        return Optional.ofNullable(parameters.get(MODIFICATION_DATE_PARAMETER)).map(DateTime::parse);
    }

    /**
     * Get the value of the {@code modification-date} parameter that can be
     * used to indicate the date at which the file was last read.
     *
     * @return {@code Optional<ZonedDateTime>}, never {@code null}
     */
    public Optional<ZonedDateTime> readDate() {
        return Optional.ofNullable(parameters.get(READ_DATE_PARAMETER)).map(DateTime::parse);
    }

    /**
     * Get the value of the {@code size} parameter that can be
     * used to indicate an approximate size of the file in octets.
     *
     * @return {@code OptionalLong}, never {@code null}
     */
    public OptionalLong size() {
        String size = parameters.get(SIZE_PARAMETER);
        if (size != null) {
            return OptionalLong.of(Long.parseLong(size));
        }
        return OptionalLong.empty();
    }

    /**
     * Get the parameters map.
     *
     * @return map, never {@code null}
     */
    public Map<String, String> parameters() {
        return Map.copyOf(parameters);
    }

    /**
     * Content disposition type.
     *
     * @return type of this content disposition
     */
    public String type() {
        return type;
    }

    /**
     * Fluent API builder for {@link ContentDisposition}.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, ContentDisposition> {
        /**
         * The form-data content disposition used by {@link io.helidon.common.media.type.MediaTypes#MULTIPART_FORM_DATA}.
         */
        public static final String TYPE_FORM_DATA = "form-data";

        private final Map<String, String> parameters = new LinkedHashMap<>();

        private String type = TYPE_FORM_DATA;

        private Builder() {
        }

        @Override
        public ContentDisposition build() {
            return new ContentDisposition(this);
        }

        /**
         * Set the content disposition type.
         * Defaults to {@value #TYPE_FORM_DATA}.
         *
         * @param type content disposition type
         * @return updated builder
         */
        public Builder type(String type) {
            this.type = type.toLowerCase();
            return this;
        }

        /**
         * Set the content disposition {@code name} parameter.
         *
         * @param name control name
         * @return this builder
         */
        public Builder name(String name) {
            parameters.put(NAME_PARAMETER, name);
            return this;
        }

        /**
         * Set the content disposition {@code filename} parameter.
         *
         * @param filename filename parameter
         * @return this builder
         */
        public Builder filename(String filename) {
            parameters.put(FILENAME_PARAMETER, URLEncoder.encode(filename, StandardCharsets.UTF_8));
            return this;
        }

        /**
         * Set the content disposition {@code creation-date} parameter.
         *
         * @param date date value
         * @return this builder
         */
        public Builder creationDate(ZonedDateTime date) {
            parameters.put(CREATION_DATE_PARAMETER, date.format(DateTime.RFC_1123_DATE_TIME));
            return this;
        }

        /**
         * Set the content disposition {@code modification-date} parameter.
         *
         * @param date date value
         * @return this builder
         */
        public Builder modificationDate(ZonedDateTime date) {
            parameters.put(MODIFICATION_DATE_PARAMETER, date.format(DateTime.RFC_1123_DATE_TIME));
            return this;
        }

        /**
         * Set the content disposition {@code read-date} parameter.
         *
         * @param date date value
         * @return this builder
         */
        public Builder readDate(ZonedDateTime date) {
            parameters.put(READ_DATE_PARAMETER, date.format(DateTime.RFC_1123_DATE_TIME));
            return this;
        }

        /**
         * Set the content disposition {@code size} parameter.
         *
         * @param size size value
         * @return this builder
         */
        public Builder size(long size) {
            parameters.put(SIZE_PARAMETER, Long.toString(size));
            return this;
        }

        /**
         * Add a new content disposition header parameter.
         *
         * @param name  parameter name
         * @param value parameter value
         * @return this builder
         */
        public Builder parameter(String name, String value) {
            parameters.put(name, value);
            return this;
        }
    }
}
