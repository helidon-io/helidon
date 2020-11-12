/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * An abstraction for a media type. Instances are immutable.
 *
 * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.7">HTTP/1.1 section 3.7</a>
 */
public final class MediaType implements AcceptPredicate<MediaType> {

    private static final Map<String, MediaType> KNOWN_TYPES;

    /**
     * The media type {@value CHARSET_PARAMETER} parameter name.
     */
    public static final String CHARSET_PARAMETER = "charset";

    /**
     * A {@link MediaType} constant representing wildcard media type.
     */
    public static final MediaType WILDCARD;

    // Common media type constants

    /**
     * A {@link MediaType} constant representing {@code application/xml} media type.
     */
    public static final MediaType APPLICATION_XML;

    /**
     * A {@link MediaType} constant representing {@code application/atom+xml} media type.
     */
    public static final MediaType APPLICATION_ATOM_XML;

    /**
     * A {@link MediaType} constant representing {@code application/xhtml+xml} media type.
     */
    public static final MediaType APPLICATION_XHTML_XML;

    /**
     * A {@link MediaType} constant representing {@code application/svg+xml} media type.
     */
    public static final MediaType APPLICATION_SVG_XML;

    /**
     * A {@link MediaType} constant representing {@code application/json} media type.
     */
    public static final MediaType APPLICATION_JSON;

    /**
     * A {@link MediaType} constant representing {@code application/stream+json} media type.
     */
    public static final MediaType APPLICATION_STREAM_JSON;

    /**
     * A {@link MediaType} constant representing {@code application/x-www-form-urlencoded} media type.
     */
    public static final MediaType APPLICATION_FORM_URLENCODED;

    /**
     * A {@link MediaType} constant representing {@code multipart/form-data} media type.
     */
    public static final MediaType MULTIPART_FORM_DATA;

    /**
     * A {@link MediaType} constant representing {@code application/octet-stream} media type.
     */
    public static final MediaType APPLICATION_OCTET_STREAM;

    /**
     * A {@link MediaType} constant representing {@code text/plain} media type.
     */
    public static final MediaType TEXT_PLAIN;

    /**
     * A {@link MediaType} constant representing {@code text/xml} media type.
     */
    public static final MediaType TEXT_XML;

    /**
     * A {@link MediaType} constant representing {@code text/html} media type.
     */
    public static final MediaType TEXT_HTML;

    /**
     * A {@link MediaType} constant representing OpenAPI yaml.
     * <p>
     * See https://github.com/opengeospatial/WFS_FES/issues/117#issuecomment-402188280
     */
    public static final MediaType APPLICATION_OPENAPI_YAML;

    /**
     * A {@link MediaType} constant representing OpenAPI json.
     */
    public static final MediaType APPLICATION_OPENAPI_JSON;

    /**
     * A {@link MediaType} constant representing "x" YAML as application.
     */
    public static final MediaType APPLICATION_X_YAML;

    /**
     * A {@link MediaType} constant representing pseudo-registered YAML. (It is not actually registered.)
     */
    public static final MediaType APPLICATION_YAML;

    /**
     * A {@link MediaType} constant representing "x" YAML as text.
     */
    public static final MediaType TEXT_X_YAML;

    /**
     * A {@link MediaType} constant representing pseudo-registered YAML as text.
     */
    public static final MediaType TEXT_YAML;

    /**
     * A {@link MediaType} constant representing {@code application/javascript} media type.
     */
    public static final MediaType APPLICATION_JAVASCRIPT;

    /**
     * A {@link MediaType} constant representing {@code text/event-stream} media type.
     */
    public static final MediaType TEXT_EVENT_STREAM;

    static {
        Map<String, MediaType> knownTypes = new HashMap<>();

        WILDCARD = new MediaType(AcceptPredicate.WILDCARD_VALUE, AcceptPredicate.WILDCARD_VALUE);
        knownTypes.put(AcceptPredicate.WILDCARD_VALUE + '/' + AcceptPredicate.WILDCARD_VALUE, WILDCARD);

        APPLICATION_XML = new MediaType("application", "xml");
        knownTypes.put("application/xml", APPLICATION_XML);

        APPLICATION_ATOM_XML = new MediaType("application", "atom+xml");
        knownTypes.put("application/atom+xml", APPLICATION_ATOM_XML);

        APPLICATION_XHTML_XML = new MediaType("application", "xhtml+xml");
        knownTypes.put("application/xhtml+xml", APPLICATION_XHTML_XML);

        APPLICATION_SVG_XML = new MediaType("application", "svg+xml");
        knownTypes.put("application/svg+xml", APPLICATION_SVG_XML);

        APPLICATION_JSON = new MediaType("application", "json");
        knownTypes.put("application/json", APPLICATION_JSON);

        APPLICATION_STREAM_JSON = new MediaType("application", "stream+json");
        knownTypes.put("application/stream+json", APPLICATION_STREAM_JSON);

        APPLICATION_FORM_URLENCODED = new MediaType("application", "x-www-form-urlencoded");
        knownTypes.put("application/x-www-form-urlencoded", APPLICATION_FORM_URLENCODED);

        MULTIPART_FORM_DATA = new MediaType("multipart", "form-data");
        knownTypes.put("multipart/form-data", MULTIPART_FORM_DATA);

        APPLICATION_OCTET_STREAM = new MediaType("application", "octet-stream");
        knownTypes.put("application/octet-stream", APPLICATION_OCTET_STREAM);

        TEXT_PLAIN = new MediaType("text", "plain");
        knownTypes.put("text/plain", TEXT_PLAIN);

        TEXT_XML = new MediaType("text", "xml");
        knownTypes.put("text/xml", TEXT_XML);

        TEXT_HTML = new MediaType("text", "html");
        knownTypes.put("text/html", TEXT_HTML);

        APPLICATION_OPENAPI_YAML = new MediaType("application", "vnd.oai.openapi");
        knownTypes.put("application/vnd.oai.openapi", APPLICATION_OPENAPI_YAML);

        APPLICATION_OPENAPI_JSON = new MediaType("application", "vnd.oai.openapi+json");
        knownTypes.put("application/vnd.oai.openapi+json", APPLICATION_OPENAPI_JSON);

        APPLICATION_X_YAML = new MediaType("application", "x-yaml");
        knownTypes.put("application/x-yaml", APPLICATION_X_YAML);

        APPLICATION_YAML = new MediaType("application", "yaml");
        knownTypes.put("application/yaml", APPLICATION_YAML);

        TEXT_X_YAML = new MediaType("text", "x-yaml");
        knownTypes.put("text-x-yaml", TEXT_X_YAML);

        TEXT_YAML = new MediaType("text", "yaml");
        knownTypes.put("text-yaml", TEXT_YAML);

        APPLICATION_JAVASCRIPT = new MediaType("application", "javascript");
        knownTypes.put("application/javascript", APPLICATION_JAVASCRIPT);

        TEXT_EVENT_STREAM = new MediaType("text", "event-stream");
        knownTypes.put("text/event-stream", TEXT_EVENT_STREAM);

        KNOWN_TYPES = Collections.unmodifiableMap(knownTypes);
    }

    // Common predicates
    /**
     * Predicate to test if {@link MediaType} is {@code application/xml} or {@code text/xml} or has {@code xml} suffix.
     */
    public static final Predicate<MediaType> XML_PREDICATE = APPLICATION_XML.or(TEXT_XML).or(mt -> mt.hasSuffix("xml"));

    /**
     * Predicate to test if {@link MediaType} is {@code application/json} or has {@code json} suffix.
     */
    public static final Predicate<MediaType> JSON_PREDICATE = APPLICATION_JSON
            .or(mt -> mt.hasSuffix("json"));

    /**
     * Predicate to test if {@link MediaType} is {@code text/event-stream} without any parameter or with parameter "element-type".
     * This "element-type" has to be equal to "application/json".
     */
    public static final Predicate<MediaType> JSON_EVENT_STREAM_PREDICATE = TEXT_EVENT_STREAM
            .and(mt -> mt.hasSuffix("event-stream"))
            .and(mt -> !mt.parameters().containsKey("element-type")
                    || "application/json".equals(mt.parameters().get("element-type")));

    /**
     * Matcher for type, subtype and attributes.
     */
    private static final CharMatcher TOKEN_MATCHER =
            CharMatcher.ascii()
                    .and(CharMatcher.javaIsoControl().negate())
                    .and(CharMatcher.isNot(' '))
                    .and(CharMatcher.noneOf("()<>@,;:\\\"/[]?="));
    private static final CharMatcher QUOTED_TEXT_MATCHER = CharMatcher.ascii().and(CharMatcher.noneOf("\"\\\r"));
    /*
     * This matches the same characters as linear-white-space from RFC 822, but we make no effort to
     * enforce any particular rules with regards to line folding as stated in the class docs.
     */
    private static final CharMatcher LINEAR_WHITE_SPACE = CharMatcher.anyOf(" \t\r\n");
    private static final String CHARSET_ATTRIBUTE = "charset";
    private final String type;
    private final String subtype;
    private final Map<String, String> parameters;

    private MediaType(String type, String subtype) {
        this.type = type;
        this.subtype = subtype;
        this.parameters = Map.of();
    }

    private MediaType(Builder builder) {
        this.type = builder.type;
        this.subtype = builder.subtype;
        boolean charsetParam = builder.charset != null && !builder.charset.isEmpty();
        if (builder.parameters.isEmpty() && !charsetParam) {
            this.parameters = Map.of();
        } else {
            Map<String, String> parameters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            parameters.putAll(builder.parameters);
            if (charsetParam) {
                parameters.put(CHARSET_PARAMETER, builder.charset);
            }
            this.parameters = Collections.unmodifiableMap(parameters);
        }
    }

    /**
     * Creates a new instance of {@code MediaType} with the supplied type and subtype.
     *
     * @param type    the primary type, {@code null} is equivalent to
     *                {@link #WILDCARD_VALUE}
     * @param subtype the subtype, {@code null} is equivalent to
     *                {@link #WILDCARD_VALUE}
     * @return a new media type for the specified type and subtype
     */
    public static MediaType create(String type, String subtype) {
        return builder()
                .type(type)
                .subtype(subtype)
                .build();
    }

    /**
     * Parses a media type from its string representation.
     *
     * @param input the input string representing a media type
     * @return parsed {@link MediaType} instance
     * @throws IllegalArgumentException if the input is not parsable
     * @throws NullPointerException     if the input is {@code null}
     */
    public static MediaType parse(String input) {
        Objects.requireNonNull(input, "Parameter 'input' is null!");
        Tokenizer tokenizer = new Tokenizer(input);
        try {
            String type = tokenizer.consumeToken(TOKEN_MATCHER);
            tokenizer.consumeCharacter('/');
            String subtype = tokenizer.consumeToken(TOKEN_MATCHER);
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
                        if ('\\' == tokenizer.previewChar()) {
                            tokenizer.consumeCharacter('\\');
                            valueBuilder.append(tokenizer.consumeCharacter(CharMatcher.ascii()));
                        } else {
                            valueBuilder.append(tokenizer.consumeToken(QUOTED_TEXT_MATCHER));
                        }
                    }
                    value = valueBuilder.toString();
                    tokenizer.consumeCharacter('"');
                } else {
                    value = tokenizer.consumeToken(TOKEN_MATCHER);
                }
                parameters.put(attribute, value);
            }
            return create(type, subtype, parameters);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("Could not parse '" + input + "'", e);
        }
    }

    /**
     * A fluent API builder for creating customized Media type instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private static String normalizeParameterValue(String attribute, String value) {
        return CHARSET_ATTRIBUTE.equals(attribute) ? Ascii.toLowerCase(value) : value;
    }

    private static MediaType create(String type, String subtype, Map<String, String> parameters) {
        Objects.requireNonNull(type, "Parameter 'type' is null!");
        Objects.requireNonNull(subtype, "Parameter 'subtype' is null!");
        Objects.requireNonNull(parameters, "Parameter 'parameters' is null!");

        String normalizedType = Tokenizer.normalize(TOKEN_MATCHER, type);
        String normalizedSubtype = Tokenizer.normalize(TOKEN_MATCHER, subtype);
        if (WILDCARD.type.equals(normalizedType)
                && !WILDCARD.type.equals(normalizedSubtype)) {
            throw new IllegalStateException(
                    "A wildcard type cannot be used with a non-wildcard subtype");
        }

        MediaType mediaType = null;
        Map<String, String> normalizedParameters = null;
        if (parameters.isEmpty()) {
            // Return one of the constants if the media type is a known type.
            mediaType = KNOWN_TYPES.get(normalizedType + '/' + normalizedSubtype);
        } else {
            normalizedParameters = new HashMap<>();
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String attribute = Tokenizer.normalize(TOKEN_MATCHER, entry.getKey());
                normalizedParameters.put(attribute, normalizeParameterValue(attribute, entry.getValue()));
            }
        }
        if (mediaType == null) {
            MediaType.Builder builder = MediaType.builder()
                    .type(normalizedType)
                    .subtype(normalizedSubtype);
            if (normalizedParameters != null) {
                builder.parameters(normalizedParameters);
            }
            mediaType = builder.build();
        }
        return mediaType;
    }

    /**
     * Getter for primary type.
     *
     * @return value of primary type.
     */
    public String type() {
        return this.type;
    }

    /**
     * Checks if the primary type is a wildcard.
     *
     * @return true if the primary type is a wildcard.
     */
    public boolean isWildcardType() {
        return this.type().equals(AcceptPredicate.WILDCARD_VALUE);
    }

    /**
     * Getter for subtype.
     *
     * @return value of subtype.
     */
    public String subtype() {
        return this.subtype;
    }

    /**
     * Checks if the subtype is a wildcard.
     *
     * @return true if the subtype is a wildcard.
     */
    public boolean isWildcardSubtype() {
        return this.subtype().equals(AcceptPredicate.WILDCARD_VALUE);
    }

    /**
     * Getter for a read-only parameter map. Keys are case-insensitive.
     *
     * @return an immutable map of parameters.
     */
    public Map<String, String> parameters() {
        return parameters;
    }

    /**
     * Create a new {@code MediaType} instance with the same type, subtype and parameters
     * copied from the original instance and the supplied {@value #CHARSET_PARAMETER} parameter.
     *
     * @param charset the {@value #CHARSET_PARAMETER} parameter value. If {@code null} or empty
     *                the {@value #CHARSET_PARAMETER} parameter will not be set or updated.
     * @return copy of the current {@code MediaType} instance with the {@value #CHARSET_PARAMETER}
     * parameter set to the supplied value.
     * @since 2.0
     */
    public MediaType withCharset(String charset) {
        return MediaType.builder()
                .type(this.type)
                .subtype(this.subtype)
                .charset(charset)
                .parameters(this.parameters)
                .build();
    }

    /**
     * Gets {@link Optional} value of charset parameter.
     *
     * @return Charset parameter.
     */
    public Optional<String> charset() {
        return Optional.ofNullable(parameters.get(CHARSET_PARAMETER));
    }

    @Override
    public double qualityFactor() {
        String q = parameters.get(AcceptPredicate.QUALITY_FACTOR_PARAMETER);
        return q == null ? 1D : Double.valueOf(q);
    }

    /**
     * Check if this media type is compatible with another media type. E.g.
     * image/* is compatible with image/jpeg, image/png, etc. Media type
     * parameters are ignored. The function is commutative.
     *
     * @param other the media type to compare with.
     * @return true if the types are compatible, false otherwise.
     */
    // fixme: Bidirectional wildcard compatibility
    @Override
    public boolean test(MediaType other) {
        return other != null && // return false if other is null, else
                (
                        type.equals(AcceptPredicate.WILDCARD_VALUE)
                                || other.type.equals(AcceptPredicate.WILDCARD_VALUE)
                                || (
                                type.equalsIgnoreCase(other.type)
                                        && (
                                        subtype.equals(AcceptPredicate.WILDCARD_VALUE) || other.subtype
                                                .equals(AcceptPredicate.WILDCARD_VALUE)))
                                || (type.equalsIgnoreCase(other.type) && this.subtype.equalsIgnoreCase(other.subtype)));
    }

    /**
     * Compares {@code obj} to this media type to see if they are the same by comparing
     * type, subtype and parameters. Note that the case-sensitivity of parameter
     * values is dependent on the semantics of the parameter name, see
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.7">HTTP/1.1</a>.
     * This method assumes that values are case-sensitive.
     * <p>
     * Note that the {@code equals(...)} implementation does not perform
     * a class equality check ({@code this.getClass() == obj.getClass()}). Therefore
     * any class that extends from {@code MediaType} class and needs to override
     * one of the {@code equals(...)} and {@link #hashCode()} methods must
     * always override both methods to ensure the contract between
     * {@link Object#equals(java.lang.Object)} and {@link Object#hashCode()} does
     * not break.
     *
     * @param obj the object to compare to.
     * @return true if the two media types are the same, false otherwise.
     */
    @SuppressWarnings("UnnecessaryJavaDocLink")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MediaType)) {
            return false;
        }

        MediaType other = (MediaType) obj;
        return (
                this.type.equalsIgnoreCase(other.type)
                        && this.subtype.equalsIgnoreCase(other.subtype)
                        && this.parameters.equals(other.parameters));
    }

    /**
     * Generate a hash code from the type, subtype and parameters.
     * <p>
     * Note that the {@link #equals(java.lang.Object)} implementation does not perform
     * a class equality check ({@code this.getClass() == obj.getClass()}). Therefore
     * any class that extends from {@code MediaType} class and needs to override
     * one of the {@link #equals(Object)} and {@code hashCode()} methods must
     * always override both methods to ensure the contract between
     * {@link Object#equals(java.lang.Object)} and {@link Object#hashCode()} does
     * not break.
     *
     * @return a generated hash code.
     */
    @SuppressWarnings("UnnecessaryJavaDocLink")
    @Override
    public int hashCode() {
        return (this.type.toLowerCase() + this.subtype.toLowerCase()).hashCode() + this.parameters.hashCode();
    }

    /**
     * Convert the media type to a string suitable for use as the value of a corresponding HTTP header.
     *
     * @return a string version of the media type.
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(type).append('/').append(subtype);
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            result.append(';').append(entry.getKey()).append('=').append(entry.getValue());
        }
        return result.toString();
    }

    /**
     * Tests if this media type has provided Structured Syntax {@code suffix} (RFC 6839).
     * <p>
     *
     * @param suffix Suffix with or without '+' prefix. If null or empty then returns {@code true} if this media type
     *               has ANY suffix.
     * @return {@code true} if media type has specified {@code suffix} or has any suffix if parameter is {@code null} or empty.
     */
    public boolean hasSuffix(String suffix) {
        if (suffix != null && !suffix.isEmpty()) {
            if (suffix.charAt(0) != '+') {
                suffix = "+" + suffix;
            }
            return subtype.endsWith(suffix);
        } else {
            return subtype.indexOf('+') >= 0;
        }
    }

    /**
     * A fluent API builder to create instances of {@link MediaType}.
     */
    public static final class Builder implements io.helidon.common.Builder<MediaType> {
        private String type = AcceptPredicate.WILDCARD_VALUE;
        private String subtype = AcceptPredicate.WILDCARD_VALUE;
        private String charset;
        private TreeMap<String, String> parameters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        private Builder() {
        }

        @Override
        public MediaType build() {
            return new MediaType(this);
        }

        /**
         * Type of the new media type.
         *
         * @param type the primary type, default is {@value #WILDCARD_VALUE}
         * @return updated builder instance
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Subtype of the new media type.
         *
         * @param subtype the secondary type, default is {@value #WILDCARD_VALUE}
         * @return updated builder instance
         */
        public Builder subtype(String subtype) {
            this.subtype = subtype;
            return this;
        }

        /**
         * Character set of the media type.
         *
         * @param charset the {@value #CHARSET_PARAMETER} parameter value. By default
         *                the {@value #CHARSET_PARAMETER} parameter will not be set.
         * @return updated builder instance
         */
        public Builder charset(String charset) {
            this.charset = charset;
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
            parameters.put(parameter.toLowerCase(), value);
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
            parameters.forEach((key, value) -> {
                this.parameters.put(key.toLowerCase(), value);
            });

            return this;
        }
    }
}
