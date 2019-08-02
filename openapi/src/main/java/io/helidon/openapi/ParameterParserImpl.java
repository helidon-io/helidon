/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.openapi;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.openapi.ParameterParser.Location;
import io.helidon.openapi.ParameterParser.Style;

/**
 * Parsers for OpenAPI parameters. Includes a builder for correctly
 * constructing parsers.
 * <p>
 * In OpenAPI, parameters are formatted according to a subset of
 * https://tools.ietf.org/html/rfc6570, using several different styles. Which
 * styles are valid depends on its location (where the parameter appears: header,
 * path, query parameter, cookie).
 * <p>
 * Instantiate this class, using the {@code Builder}, once for each separate parameter.
 * An instance can be reused for parsing that same parameter in multiple incoming
 * requests.
 */
public abstract class ParameterParserImpl implements ParameterParser {

    /**
     * Builder for a {@code ParameterParserImpl}.
     */
    static class Builder implements ParameterParser.Builder {

        private final Location location;
        private final Style style;
        private final String paramName;
        private Optional<Boolean> exploded = Optional.empty();

        private final Map<Style, BiFunction<String, Boolean, ParameterParser>> parserFactories
                = initParserFactories();

        private static Map<Style, BiFunction<String, Boolean, ParameterParser>> initParserFactories() {
            Map<Style, BiFunction<String, Boolean, ParameterParser>> result = new EnumMap<>(Style.class);

            result.put(Style.SIMPLE, SimpleParser::new);
            result.put(Style.LABEL, LabelParser::new);
            result.put(Style.MATRIX, MatrixParser::new);
            result.put(Style.FORM, FormParser::new);
            result.put(Style.SPACE_DELIMITED, SpaceDelimitedParser::new);
            result.put(Style.PIPE_DELIMITED, PipeDelimitedParser::new);
            result.put(Style.DEEP_OBJECT, DeepObjectParser::new);

            return result;
        }

        /**
         * Creates a builder with the required parameter name, location, and style.
         *
         * @param paramName parameter to be parsed
         * @param locationName name of the location where the parameter exists
         * @param styleName name of the style to be applied to the parameter value
         * @throws {@code IllegalArgumentException} if the location name or style name
         * are unrecognized or if the location does not support the given style
         */
        Builder(String paramName, Location location, Style style) {
            this.paramName = paramName;
            this.location = location;
            this.style = style;
            if (!location.supportsStyle(style)) {
                throw new IllegalArgumentException("Location " + location.name()
                        + " does not support style " + style.name());
            }
        }

        /**
         * Sets whether the parameter is expressed in exploded format.
         *
         * @param exploded true if the parameter is in exploded format; false otherwise
         * @return the {@code Builder} instance
         */
        @Override
        public Builder exploded(boolean exploded) {
            this.exploded = Optional.of(exploded);
            return this;
        }

        /**
         * Constructs the parser using the assigned builder settings.
         *
         * @return the configured {@code ParameterParserImpl}
         */
        @Override
        public ParameterParser build() {

            return parserFactories.get(style).apply(paramName,
                    exploded.orElse(location.explodeByDefault()));
        }
    }

    /**
     * Returns a builder ready to be set up by invoking the builder methods.
     *
     * @param paramName name of the parameter to be parsed
     * @param location {@code Location} where the parameter exists
     * @param style {@code Style} in which the parameter value is expressed.
     *
     * @return a {@code Builder}
     */
    public static ParameterParser.Builder builder(String paramName, Location location, Style style) {
        return new Builder(paramName, location, style);
    }

    private final String paramName;
    private final boolean exploded;
    private final boolean includeID;

    private ParameterParserImpl(String paramName, Boolean exploded, boolean includeID) {
        this.paramName = paramName;
        this.exploded = exploded;
        this.includeID = includeID;
    }

    /**
     * Parses the specified value according to the configured attributes of the
     * parser. The value String can contain multiple values and, if
     * {@code explode} is true (or if {@code explode} is absent and the default
     * explode setting is true), then each value appears as a separate parameter
     * occurrence.
     *
     * @param value the String to be parsed
     * @return {@code List} of {@code String}s parsed from the parameter
     */
    public List<String> parse(String value) {
        try {
            value = URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
        final Pattern pattern = pattern();
        final List<String> result = new ArrayList<>();
        final Matcher m = pattern.matcher(valueToParse(value));
        while (m.find()) {
            result.add(m.group(1));
        }
        if (!m.hitEnd()) {
            final StringBuffer sb = new StringBuffer();
            m.appendTail(sb);
            throw new IllegalArgumentException(
                    "Unexpected extra text in the value not matched during parsing: "
            + sb.toString());
        }
        return result;
    }

    /**
     * Parses the specified list of values according to the configured
     * attributes of the parser.Each individual value can itself contain
     * multiple values.
     *
     * @param values the Strings to be parsed
     * @return {@code List} of {@code String}s parsed from the input values
     */
    public List<String> parse(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String v : values) {
            result.addAll(parse(v));
        }
        return result;
    }

    abstract Pattern pattern();

    String valueToParse(String originalValue) {
        return originalValue;
    }

    boolean includeID() {
        return includeID;
    }

    String paramName() {
        return paramName;
    }

    boolean exploded() {
        return exploded;
    }

    abstract String prefix();


    /**
     * Parses values delimited (separated) by a given character.
     * <p>
     * If the values are expressed as exploded then each value looks like
     * {@code paramName=value} with the delimiter appearing between each value.
     * For example, if the delimiter is a comma: {@code id=a,id=b,id=c}.
     * <p>
     * If the values are not exploded, then the set of values is introduced by a
     * single {@code paramName=} and then the actual values appear separated by
     * the delimiter. For example, with a comma delimiter: {@code id=a,b,c}.
     */
    private abstract static class DelimitedParser extends ParameterParserImpl {

        private final String delimiter;

        DelimitedParser(String paramName, Boolean exploded, boolean includeID, String delimiter) {
            super(paramName, exploded, includeID);
            this.delimiter = delimiter;
        }

        @Override
        String valueToParse(String originalValue) {
            if (exploded()) {
                return originalValue;
            }
                return originalValue.substring(prefix().length());
        }

        @Override
        Pattern pattern() {
            final Pattern pattern;
            if (exploded()) {
                pattern = Pattern.compile("(?:" + paramName() + "=)([^" + delimiter + "]+)");
            } else {
                pattern = Pattern.compile("([^" + delimiter + "]+)");
            }
            return pattern;
        }

        @Override
        String prefix() {
            return "";
        }
    }

    /**
     * Parses styles that use a prefix before each value (as opposed to a separator
     * between value).
     * <p>
     * If values were not exploded, then the value string will have this format:
     * {@code <prefix>separator><value><separator><value>...}
     * <p>
     * On the other hand, if values were exploded, then the value string looks like this:
     * {@code <prefix><value><prefix><value>...}
     *
     */
    private abstract static class PrefixedParser extends ParameterParserImpl {

        PrefixedParser(String paramName, Boolean exploded, boolean includeID) {
            super(paramName, exploded, includeID);
        }

        abstract String separator();

        @Override
        String valueToParse(String originalValue) {
            if (exploded()) {
                return originalValue;
            }
            final String prefix = prefix();
            if (originalValue.startsWith(prefix)) {
                return originalValue.substring(prefix.length());
            }
            throw new IllegalArgumentException(
                    "Parameter value '" + originalValue
                    + "' does not begin with expected prefix "
                    + prefix);
        }

        @Override
        Pattern pattern() {
            if (exploded()) {
                return Pattern.compile("(?:"
                        + Pattern.quote(prefix())
                        + ")([^"
                        + Pattern.quote(prefix())
                        + "]+)");
            }
            return Pattern.compile("([^"
                    + Pattern.quote(separator())
                    + "]+)");
        }
    }

    private static class SimpleParser extends DelimitedParser {

        SimpleParser(String paramName, Boolean exploded) {
            super(paramName, exploded, false, ",");
        }
    }

    private static class LabelParser extends PrefixedParser {

        LabelParser(String paramName, Boolean exploded) {
            super(paramName, exploded, false);
        }

        @Override
        String prefix() {
            return ".";
        }

        @Override
        String separator() {
            return ",";
        }
    }

    private static class MatrixParser extends PrefixedParser {

        MatrixParser(String paramName, Boolean exploded) {
            super(paramName, exploded, true);
        }

        @Override
        String prefix() {
            return paramIntroducer();
        }

        @Override
        String separator() {
            return (exploded() ? paramIntroducer() : ",");
        }

        @Override
        String valueToParse(String originalValue) {
            return (exploded() ? originalValue
                    : originalValue.substring(prefix().length()));
        }

        private String paramIntroducer() {
            return ";" + paramName() + "=";
        }
    }

    private static class FormParser extends DelimitedParser {

        FormParser(String paramName, Boolean exploded) {
            super(paramName, exploded, true, "&");
        }

        @Override
        String valueToParse(String originalValue) {
            return (exploded() ? originalValue
                    : originalValue.substring(prefix().length()));
        }

        @Override
        String prefix() {
            return paramName() + "=";
        }
    }

    private static class SpaceDelimitedParser extends DelimitedParser {

        SpaceDelimitedParser(String paramName, Boolean exploded) {
            super(paramName, exploded, true, " ");
        }
    }

    private static class PipeDelimitedParser extends DelimitedParser {

        PipeDelimitedParser(String paramName, Boolean exploded) {
            super(paramName, exploded, true, "\\|");
        }
    }

    private static class DeepObjectParser extends ParameterParserImpl {

        DeepObjectParser(String paramName, Boolean exploded) {
            super(paramName, exploded, true);
        }

        @Override
        public List<String> parse(String value) {
            final List<String> result = new ArrayList<>();
            return result;
        }

        @Override
        Pattern pattern() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        String prefix() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
