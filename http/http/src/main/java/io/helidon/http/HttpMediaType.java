/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.media.type.ParserMode;

/**
 * Media type used in HTTP headers, in addition to the media type definition, these may contain additional
 * parameters, such as {@link #QUALITY_FACTOR_PARAMETER} and {@link #CHARSET_PARAMETER}.
 *
 * @see io.helidon.http.HttpMediaTypes
 */
public sealed interface HttpMediaType extends Predicate<HttpMediaType>,
                                              Comparable<HttpMediaType>,
                                              MediaType permits HttpMediaTypeImpl {
    /**
     * The media type {@value} parameter name.
     */
    String CHARSET_PARAMETER = "charset";
    /**
     * The media type quality factor {@value} parameter name.
     */
    String QUALITY_FACTOR_PARAMETER = "q";

    /**
     * A fluent API builder for creating customized Media type instances.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new HTTP media type from media type.
     *
     * @param mediaType media type
     * @return a new HTTP media type without any parameters
     */
    static HttpMediaType create(MediaType mediaType) {
        return HttpMediaType.builder()
                .mediaType(mediaType)
                .build();
    }

    /**
     * Parse media type from the provided string.
     * Strict media type parsing mode is used.
     *
     * @param mediaTypeString media type string
     * @return HTTP media type parsed from the string
     */
    static HttpMediaType create(String mediaTypeString) {
        return Builder.parse(mediaTypeString, ParserMode.STRICT);
    }

    /**
     * Parse media type from the provided string.
     *
     * @param mediaTypeString media type string
     * @param parserMode media type parsing mode
     * @return HTTP media type parsed from the string
     */
    static HttpMediaType create(String mediaTypeString, ParserMode parserMode) {
        return Builder.parse(mediaTypeString, parserMode);
    }

    /**
     * The underlying media type.
     *
     * @return media type
     */
    MediaType mediaType();

    /**
     * Quality factor, if not defined, defaults to 1.
     *
     * @return quality factor
     */
    double qualityFactor();

    /**
     * Read-only parameter map. Keys are case-insensitive.
     *
     * @return an immutable map of parameters.
     */
    Map<String, String> parameters();

    /**
     * Gets {@link java.util.Optional} value of charset parameter.
     *
     * @return Charset parameter.
     */
    default Optional<String> charset() {
        return Optional.ofNullable(parameters().get(CHARSET_PARAMETER));
    }

    /**
     * Check if this media type is compatible with another media type. E.g.
     * image/* is compatible with image/jpeg, image/png, etc. Media type
     * parameters are ignored. The function is commutative.
     *
     * @param other the media type to compare with.
     * @return true if the types are compatible, false otherwise.
     */
    @Override
    boolean test(HttpMediaType other);

    /**
     * Check if this media type is compatible with another media type. E.g.
     * image/* is compatible with image/jpeg, image/png, etc. Media type
     * parameters are ignored. The function is commutative.
     *
     * @param mediaType the media type to compare with.
     * @return true if the types are compatible, false otherwise.
     */
    boolean test(MediaType mediaType);

    @Override
    default String type() {
        return mediaType().type();
    }

    @Override
    default String subtype() {
        return mediaType().subtype();
    }

    /**
     * Create a new {@link HttpMediaType} instance with the same type, subtype and parameters
     * copied from the original instance and the supplied {@value #CHARSET_PARAMETER} parameter.
     *
     * @param charset the {@value #CHARSET_PARAMETER} parameter value. If {@code null} or empty
     *                the {@value #CHARSET_PARAMETER} parameter will not be set or updated.
     * @return copy of the current {@code MediaType} instance with the {@value #CHARSET_PARAMETER}
     *         parameter set to the supplied value.
     */
    default HttpMediaType withCharset(String charset) {
        return builder()
                .mediaType(mediaType())
                .parameters(parameters())
                .charset(charset)
                .build();
    }

    /**
     * Create a new {@link HttpMediaType} instance with the same type, subtype and parameters
     * copied from the original instance and the supplied {@value #CHARSET_PARAMETER} parameter.
     *
     * @param charset the {@value #CHARSET_PARAMETER} parameter value
     * @return copy of the current {@code MediaType} instance with the {@value #CHARSET_PARAMETER}
     *         parameter set to the supplied value.
     */
    default HttpMediaType withCharset(Charset charset) {
        return withCharset(charset.name());
    }

    /**
     * Text of this media type, to be used on the wire.
     *
     * @return text including all parameters
     */
    String text();

    /**
     * Create a new {@link HttpMediaType} instance with the same type, subtype and parameters
     * copied from the original instance and the supplied custom parameter.
     *
     * @param name  name of the parameter
     * @param value value of the parameter
     * @return copy of the current {@code MediaType} instance with the {@value #CHARSET_PARAMETER}
     *         parameter set to the supplied value.
     */
    default HttpMediaType withParameter(String name, String value) {
        return builder()
                .mediaType(mediaType())
                .parameters(parameters())
                .addParameter(name, value)
                .build();
    }

    /**
     * Fluent API builder for {@link HttpMediaType}.
     */
    class Builder implements io.helidon.common.Builder<Builder, HttpMediaType> {
        private final Map<String, String> parameters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        private MediaType mediaType = MediaTypes.WILDCARD;

        private Builder() {
        }

        @Override
        public HttpMediaType build() {
            return new HttpMediaTypeImpl(this);
        }

        /**
         * Media type to use.
         *
         * @param mediaType media type
         * @return updated builder
         */
        public Builder mediaType(MediaType mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        /**
         * Charset parameter to use.
         *
         * @param charset charset
         * @return updated builder
         */
        public Builder charset(String charset) {
            parameters.put(CHARSET_PARAMETER, charset);
            return this;
        }

        /**
         * Add a new parameter to the parameter map.
         *
         * @param parameter name of the parameter to add
         * @param value     value of the parameter to add
         * @return updated builder instance
         */
        public Builder addParameter(String parameter, String value) {
            parameters.put(parameter.toLowerCase(Locale.ROOT), value);
            return this;
        }

        /**
         * Parameters of the media type.
         *
         * @param parameters a map of media type parameters, default is empty
         * @return updated builder instance
         */
        public Builder parameters(Map<String, String> parameters) {
            this.parameters.clear();
            parameters.forEach((key, value) -> this.parameters.put(key.toLowerCase(Locale.ROOT), value));

            return this;
        }

        /**
         * Quality factor parameter to use.
         *
         * @param q quality factor
         * @return updated builder
         */
        public Builder q(double q) {
            addParameter(QUALITY_FACTOR_PARAMETER, String.valueOf(q));
            return this;
        }

        Map<String, String> parameters() {
            return parameters;
        }

        MediaType mediaType() {
            return mediaType;
        }

        private static HttpMediaType parse(String mediaTypeString, ParserMode parserMode) {
            Objects.requireNonNull(mediaTypeString);
            Objects.requireNonNull(parserMode);
            if (mediaTypeString.indexOf(';') == -1) {
                MediaType mediaType = MediaTypes.create(mediaTypeString, parserMode);
                if (parserMode == ParserMode.STRICT) {
                    HttpToken.validate(mediaType.type());
                    HttpToken.validate(mediaType.subtype());
                }
                return builder().mediaType(mediaType).build();
            }
            try {
                return new StrictParser(mediaTypeString).parse();
            } catch (IllegalArgumentException e) {
                if (parserMode == ParserMode.STRICT) {
                    throw e;
                }
            }
            return parseRelaxed(mediaTypeString);
        }

        private static HttpMediaType parseRelaxed(String mediaTypeString) {
            Builder b = builder();
            int index = mediaTypeString.indexOf(';');
            if (index != -1) {
                b.mediaType(MediaTypes.create(mediaTypeString.substring(0, index)));
                String[] params = mediaTypeString.substring(index + 1).split(";");
                // each param is key=value
                for (String param : params) {
                    int eq = param.indexOf('=');
                    if (eq == -1) {
                        throw new IllegalArgumentException("Invalid media type, param does not contain =");
                    }
                    String value = param.substring(eq + 1).trim();
                    if (!value.isEmpty()) {
                        // in case the value is "text/plain; charset=" we treat it as if charset was not defined
                        // same for any other parameter

                        // dequote
                        if (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                            value = value.substring(1, value.length() - 1);
                        }
                        b.addParameter(param.substring(0, eq).trim(), value);
                    }
                }
            } else {
                b.mediaType(MediaTypes.create(mediaTypeString, ParserMode.RELAXED));
            }
            return b.build();
        }

        private static final class StrictParser {
            private final String value;
            private int index;

            private StrictParser(String value) {
                this.value = value;
            }

            private static boolean isOptionalWhitespace(char ch) {
                return ch == ' ' || ch == '\t';
            }

            private static boolean isQuotedText(char ch) {
                return ch == '\t'
                        || ch == ' '
                        || ch == 0x21
                        || ch >= 0x23 && ch <= 0x5b
                        || ch >= 0x5d && ch <= 0x7e
                        || ch >= 0x80 && ch <= 0xff;
            }

            private static boolean isQuotedPairCharacter(char ch) {
                return ch == '\t'
                        || ch == ' '
                        || ch >= 0x21 && ch <= 0x7e
                        || ch >= 0x80 && ch <= 0xff;
            }

            private static IllegalArgumentException invalid(String message) {
                return new IllegalArgumentException("Invalid media type: " + message);
            }

            private HttpMediaType parse() {
                if (value.indexOf('/') < 1) {
                    throw new IllegalArgumentException("Cannot parse media type: " + value);
                }
                Builder builder = builder();
                String type = parseUntil('/');
                require('/');
                String subtype = parseSubtype();
                builder.mediaType(MediaTypes.create(type, subtype));

                while (!atEnd()) {
                    skipOptionalWhitespace();
                    if (atEnd()) {
                        throw invalid("Expected ';'");
                    }
                    require(';');
                    skipOptionalWhitespace();
                    if (atEnd() || current() == ';') {
                        continue;
                    }

                    String parameterName = parseUntil('=');
                    require('=');
                    if (atEnd()) {
                        throw invalid("Expected parameter value");
                    }
                    String parameterValue = current() == '"' ? parseQuotedString() : parseParameterToken();
                    builder.addParameter(parameterName, parameterValue);
                }
                return builder.build();
            }

            private String parseSubtype() {
                int start = index;
                while (!atEnd() && current() != ';' && !isOptionalWhitespace(current())) {
                    index++;
                }
                return validateToken(start);
            }

            private String parseParameterToken() {
                int start = index;
                while (!atEnd() && current() != ';' && !isOptionalWhitespace(current())) {
                    index++;
                }
                return validateToken(start);
            }

            private String parseUntil(char delimiter) {
                int start = index;
                while (!atEnd() && current() != delimiter) {
                    index++;
                }
                return validateToken(start);
            }

            private String validateToken(int start) {
                String token = value.substring(start, index);
                HttpToken.validate(token);
                return token;
            }

            private String parseQuotedString() {
                index++;
                StringBuilder result = new StringBuilder();
                while (!atEnd()) {
                    char ch = current();
                    index++;
                    if (ch == '"') {
                        return result.toString();
                    }
                    if (ch == '\\') {
                        if (atEnd()) {
                            throw invalid("Incomplete quoted-pair");
                        }
                        ch = current();
                        index++;
                        if (!isQuotedPairCharacter(ch)) {
                            throw invalid("Invalid quoted-pair character");
                        }
                    } else if (!isQuotedText(ch)) {
                        throw invalid("Invalid quoted-string character");
                    }
                    result.append(ch);
                }
                throw invalid("Unterminated quoted-string");
            }

            private void skipOptionalWhitespace() {
                while (!atEnd() && isOptionalWhitespace(current())) {
                    index++;
                }
            }

            private void require(char expected) {
                if (atEnd() || current() != expected) {
                    throw invalid("Expected '" + expected + "'");
                }
                index++;
            }

            private boolean atEnd() {
                return index == value.length();
            }

            private char current() {
                return value.charAt(index);
            }
        }
    }
}
