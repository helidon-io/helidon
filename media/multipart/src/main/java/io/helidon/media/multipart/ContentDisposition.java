/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.multipart;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import io.helidon.common.http.CharMatcher;
import io.helidon.common.http.Http;
import io.helidon.common.http.Tokenizer;

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
public final class ContentDisposition {

    private static final CharMatcher TOKEN_MATCHER = CharMatcher.ascii()
            .and(CharMatcher.javaIsoControl().negate())
            .and(CharMatcher.isNot(' '))
            .and(CharMatcher.noneOf("()<>@,;:\\\"/[]?="));

    private static final CharMatcher LINEAR_WHITE_SPACE = CharMatcher.anyOf(" \t\r\n");

    private static final CharMatcher QUOTED_TEXT_MATCHER = CharMatcher.ascii()
            .and(CharMatcher.noneOf("\"\\\r"));

    private static final String NAME_PARAMETER = "name";
    private static final String FILENAME_PARAMETER = "filename";
    private static final String CREATION_DATE_PARAMETER = "creation-date";
    private static final String MODIFICATION_DATE_PARAMETER = "modification-date";
    private static final String READ_DATE_PARAMETER = "read-date";
    private static final String SIZE_PARAMETER = "size";

    /**
     * Empty content disposition.
     */
    static final ContentDisposition EMPTY = new ContentDisposition("", Collections.emptyMap());

    private final String type;
    private final Map<String, String> parameters;

    /**
     * Create a new instance.
     * @param type content disposition type
     * @param params content disposition parameters
     */
    private ContentDisposition(String type, Map<String, String> params) {
        this.type = type;
        this.parameters = params;
    }

    /**
     * The content disposition type.
     * @return type, never {@code null}
     */
    public String type() {
        return type;
    }

    /**
     * Get the value of the {@code name} parameter. In the case of a
     * {@code form-data} disposition type the value is the original field name
     * from the form.
     *
     * @return {@code Optional<String>}, never {@code null}
     */
    public Optional<String> name() {
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
        String filename;
        try {
            String value = parameters.get(FILENAME_PARAMETER);
            if (value != null) {
                filename = URLDecoder.decode(value, "UTF-8");
            } else {
                filename = null;
            }
        } catch (UnsupportedEncodingException ex) {
            filename = null;
        }
        return Optional.ofNullable(filename);
    }

    /**
     * Get the value of the {@code creation-date} parameter that can be used
     * to indicate the date at which the file was created.
     * @return {@code Optional<ZonedDateTime>}, never {@code null}
     */
    public Optional<ZonedDateTime> creationDate() {
        return Optional.ofNullable(parameters.get(CREATION_DATE_PARAMETER)).map(Http.DateTime::parse);
    }

    /**
     * Get the value of the {@code modification-date} parameter that can be
     * used to indicate the date at which the file was last modified.
     *
     * @return {@code Optional<ZonedDateTime>}, never {@code null}
     */
    public Optional<ZonedDateTime> modificationDate() {
        return Optional.ofNullable(parameters.get(MODIFICATION_DATE_PARAMETER)).map(Http.DateTime::parse);
    }

    /**
     * Get the value of the {@code modification-date} parameter that can be
     * used to indicate the date at which the file was last read.
     *
     * @return {@code Optional<ZonedDateTime>}, never {@code null}
     */
    public Optional<ZonedDateTime> readDate() {
        return Optional.ofNullable(parameters.get(READ_DATE_PARAMETER)).map(Http.DateTime::parse);
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
     * @return map, never {@code null}
     */
    public Map<String, String> parameters() {
        return parameters;
    }

    /**
     * Convert the content disposition to a string suitable for use as the value
     * of a corresponding HTTP header.
     *
     * @return a string version of the content disposition
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type);
        for (Entry<String, String> param : parameters.entrySet()) {
            sb.append(";");
            sb.append(param.getKey());
            sb.append("=");
            sb.append(param.getValue());
        }
        return sb.toString();
    }

    /**
     * Create a new builder instance.
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Parse the header value of a {@code Content-Disposition} header.
     * @param input header value to parse
     * @return ContentDisposition instance
     * @throws IllegalArgumentException if a parsing error occurs
     */
    static ContentDisposition parse(String input) {
        Objects.requireNonNull(input, "Parameter 'input' is null!");
        Tokenizer tokenizer = new Tokenizer(input.trim());
        try {
            String type = tokenizer.consumeToken(TOKEN_MATCHER).toLowerCase();
            Map<String, String> parameters = new HashMap<>();
            while (tokenizer.hasMore()) {
                tokenizer.consumeTokenIfPresent(LINEAR_WHITE_SPACE);
                tokenizer.consumeCharacter(';');
                tokenizer.consumeTokenIfPresent(LINEAR_WHITE_SPACE);
                String attribute = tokenizer.consumeToken(TOKEN_MATCHER);
                tokenizer.consumeCharacter('=');
                final String value;
                if ('"' == tokenizer.previewChar()) {
                    tokenizer.consumeCharacter('"');
                    StringBuilder valueBuilder = new StringBuilder();
                    while ('"' != tokenizer.previewChar()) {
                        // quoted pair
                        // '\' escapes '"' or '\'
                        if ('\\' == tokenizer.previewChar()) {
                            tokenizer.consumeCharacter('\\');
                            char c = tokenizer.previewChar();
                            if ('"' == c || '\\' == c) {
                                // process
                                valueBuilder.append(tokenizer.consumeCharacter(CharMatcher.ascii()));
                                continue;
                            } else {
                                valueBuilder.append('\\');
                            }
                        }
                        valueBuilder.append(tokenizer.consumeToken(QUOTED_TEXT_MATCHER));
                    }
                    value = valueBuilder.toString();
                    tokenizer.consumeCharacter('"');
                } else {
                    value = tokenizer.consumeToken(TOKEN_MATCHER);
                }
                parameters.put(attribute, value);
            }
            return new ContentDisposition(type, parameters);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("Could not parse '" + input + "'", e);
        }
    }

    /**
     * Builder class to create {@link ContentDisposition} instances.
     */
    public static final class Builder implements io.helidon.common.Builder<ContentDisposition> {

        private String type;
        private final Map<String, String> params = new HashMap<>();

        /**
         * Set the content disposition type.
         * @param type content disposition type
         * @return this builder
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Set the content disposition {@code name} parameter.
         * @param name control name
         * @return this builder
         */
        public Builder name(String name) {
            params.put("name", name);
            return this;
        }

        /**
         * Set the content disposition {@code filename} parameter.
         * @param filename filename parameter
         * @return this builder
         * @throws IllegalStateException if an
         * {@link UnsupportedEncodingException} exception is thrown for
         * {@code UTF-8}
         */
        public Builder filename(String filename) {
            try {
                params.put("filename", URLEncoder.encode(filename, "UTF-8"));
            } catch (UnsupportedEncodingException ex) {
                throw new IllegalStateException(ex);
            }
            return this;
        }

        /**
         * Set the content disposition {@code creation-date} parameter.
         * @param date date value
         * @return this builder
         */
        public Builder creationDate(ZonedDateTime date) {
            params.put("creation-date", date.format(Http.DateTime.RFC_1123_DATE_TIME));
            return this;
        }

        /**
         * Set the content disposition {@code modification-date} parameter.
         * @param date date value
         * @return this builder
         */
        public Builder modificationDate(ZonedDateTime date) {
            params.put("modification-date", date.format(Http.DateTime.RFC_1123_DATE_TIME));
            return this;
        }

        /**
         * Set the content disposition {@code read-date} parameter.
         * @param date date value
         * @return this builder
         */
        public Builder readDate(ZonedDateTime date) {
            params.put("read-date", date.format(Http.DateTime.RFC_1123_DATE_TIME));
            return this;
        }

        /**
         * Set the content disposition {@code size} parameter.
         * @param size size value
         * @return this builder
         */
        public Builder size(long size) {
            params.put("size", Long.toString(size));
            return this;
        }

        /**
         * Add a new content disposition header parameter.
         * @param name parameter name
         * @param value parameter value
         * @return this builder
         */
        public Builder parameter(String name, String value) {
            params.put(name, value);
            return this;
        }

        @Override
        public ContentDisposition build() {
            return new ContentDisposition(type, params);
        }
    }
}
