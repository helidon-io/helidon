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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

import io.helidon.openapi.ParameterParserImpl.DeepObjectParser;
import io.helidon.openapi.ParameterParserImpl.FormParser;
import io.helidon.openapi.ParameterParserImpl.LabelParser;
import io.helidon.openapi.ParameterParserImpl.MatrixParser;
import io.helidon.openapi.ParameterParserImpl.PipeDelimitedParser;
import io.helidon.openapi.ParameterParserImpl.SimpleParser;
import io.helidon.openapi.ParameterParserImpl.SpaceDelimitedParser;

/**
 * Abstraction of parsers for OpenAPI parameters. Includes a builder for
 * correctly constructing parsers.
 * <p>
 * In OpenAPI, parameters are formatted according to a subset of
 * https://tools.ietf.org/html/rfc6570, using several different styles. Which
 * styles are valid depends on its location (where the parameter appears:
 * header, path, query parameter, cookie).
 * <p>
 * Instantiate this class, using the {@code Builder}, once for each separate
 * parameter. An instance can be reused for parsing that same parameter in
 * multiple incoming requests.
 */
interface ParameterParser {

    /**
     * Returns a {@code Builder} for use in constructing a new
     * {@code ParameterParser}.
     *
     * @param paramName name of the parameter to be parsed
     * @param location {@code Location} where the parameter exists
     * @return a {@code Builder} for parsing the parameter
     */
    static Builder builder(String paramName, Location location) {
        return ParameterParserImpl.builder(paramName, location);
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
    List<String> parse(String value);

    /**
     * Parses the specified list of values according to the configured
     * attributes of the parser.Each individual value can itself contain
     * multiple values.
     *
     * @param values the Strings to be parsed
     * @return {@code List} of {@code String}s parsed from the input values
     */
    List<String> parse(List<String> values);

    /**
     * A {@code Builder} for {@code ParameterParser}s.
     * <p>
     * Instantiate the {@code Builder} itself using the
     * {@code ParameterParser#builder} method, which requires the parameter name
     * and location. Then optionally invoke the builder's methods to set other
     * characteristics, finally using {@link #build() } to create the
     * properly-initialized parser.
     */
    interface Builder extends io.helidon.common.Builder<ParameterParser> {

        /**
         * Sets the style with which the parameter is formatted.
         * <p>
         * Note that each {@code Location} has a default style if you do not
         * specify one.
         *
         * @param style {@code Style} used for the parameter
         * @return the {@code Builder}
         */
        Builder style(Style style);

        /**
         * Sets whether the parameter is expressed in exploded format.
         *
         * @param exploded true if the parameter is in exploded format; false
         * otherwise
         * @return the {@code Builder} instance
         */
        Builder exploded(boolean exploded);

        /**
         * Constructs the parser using the assigned builder settings.
         *
         * @return the configured {@code ParameterParserImpl}
         */
        @Override
        ParameterParser build();
    }

    /**
     * Locations in a request where parameters are found.
     * <p>
     * Each parameter location has a default {@code Style}, a default
     * {@code explode} setting, and a collection of {@code Style}s which are
     * valid for that location.
     */
    enum Location {
        PATH(Style.SIMPLE, false, Style.SIMPLE, Style.LABEL, Style.MATRIX),
        QUERY(Style.FORM, true, Style.FORM, Style.SPACE_DELIMITED, Style.PIPE_DELIMITED, Style.DEEP_OBJECT),
        HEADER(Style.SIMPLE, false, Style.SIMPLE),
        COOKIE(Style.FORM, true, Style.FORM);

        private final Style defaultStyle;
        private final boolean defaultExplode;
        private final List<Style> supportedStyles;

        Location(Style defaultStyle, boolean defaultExplode, Style... styles) {
            this.defaultStyle = defaultStyle;
            this.defaultExplode = defaultExplode;
            this.supportedStyles = Arrays.asList(styles);
        }

        /**
         * Returns the default {@code Style} for this {@code Location}.
         *
         * @return default {@code Style}
         */
        Style defaultStyle() {
            return defaultStyle;
        }

        /**
         * Tells whether this {@code Location} supports the specified
         * {@code Style}.
         *
         * @param style the {@code Style} to check
         * @return whether this {@code Location} supports the specified
         * {@code Style}
         */
        boolean supportsStyle(Style style) {
            return supportedStyles.contains(style);
        }

        /**
         * Tells whether this {@code Location} uses exploded formatting by
         * default.
         *
         * @return if the {@code Location} uses exploded by default
         */
        boolean explodeByDefault() {
            return defaultExplode;
        }

        /**
         * Returns the {@code Location} value that matches the specified location
         * name.
         *
         * @param locationName
         * @return matching {@code Location}
         * @throws IllegalArgumentException if the name matches no {@code Location}
         */
        static Location match(String locationName) {
            return Enum.valueOf(Location.class, locationName.toUpperCase(Locale.ENGLISH));
        }
    }

    /**
     * Styles with which parameter values can be represented in
     * OpenAPI-compatible requests.
     */
    enum Style {
        SIMPLE(SimpleParser::new),
        LABEL(LabelParser::new),
        MATRIX(MatrixParser::new),
        FORM(FormParser::new),
        SPACE_DELIMITED(SpaceDelimitedParser::new, "spaceDelimited"),
        PIPE_DELIMITED(PipeDelimitedParser::new, "pipeDelimited"),
        DEEP_OBJECT(DeepObjectParser::new, "deepObject");

        /**
         * Returns the {@code Style} value that matches the specified style
         * name.
         *
         * @param styleName
         * @return matching {@code Style}
         * @throws IllegalArgumentException if the name matches no {@code Style}
         */
        static Style match(String styleName) {
            for (Style s : Style.values()) {
                if (s.styleName.equals(styleName)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Cannot find ParameterParser.Style matching style name " + styleName);
        }

        private String styleName = name().toLowerCase(Locale.ENGLISH);
        private final BiFunction<String, Boolean, ParameterParserImpl> factory;

        Style(BiFunction<String, Boolean, ParameterParserImpl> factory) {
            this.factory = factory;
        }

        Style(BiFunction<String, Boolean, ParameterParserImpl> factory, String styleName) {
            this.factory = factory;
            this.styleName = styleName;
        }

        /**
         * Returns a parser of this {@code Style} for the given parameter and
         * exploded setting.
         *
         * @param paramName parameter name that will be parsed
         * @param exploded whether exploded format is used
         * @return a {@code ParameterParser} properly configured for this {@code Style}
         */
        ParameterParser parser(String paramName, boolean exploded) {
            return factory.apply(paramName, exploded);
        }
    }
}
