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

/**
 * Abstraction of parsers for OpenAPI parameters. Includes a builder for correctly
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
interface ParameterParser {

    interface Builder extends io.helidon.common.Builder<ParameterParser> {

        /**
         * Sets whether the parameter is expressed in exploded format.
         *
         * @param exploded true if the parameter is in exploded format; false otherwise
         * @return the {@code Builder} instance
         */
        Builder exploded(boolean exploded);

        /**
         * Constructs the parser using the assigned builder settings.
         *
         * @return the configured {@code ParameterParserImpl}
         */
        ParameterParser build();
    }

    /**
     * Locations in a request where parameters might be found.
     * <p>
     * Each parameter location has a default {@code Style}, a default
     * {@code explode} setting, and a collection of {@code Style}s valid for
     * that location.
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

        boolean supportsStyle(Style style) {
            return supportedStyles.contains(style);
        }

        boolean explodeByDefault() {
            return defaultExplode;
        }

        static Location match(String locationName) {
            return Enum.valueOf(Location.class, locationName.toUpperCase(Locale.ENGLISH));
        }
    }

    enum Style {
        SIMPLE,
        LABEL,
        MATRIX,
        FORM,
        SPACE_DELIMITED("spaceDelimited"),
        PIPE_DELIMITED("pipeDelimited"),
        DEEP_OBJECT("deepObject");

        static Style match(String styleName) {
            for (Style s : Style.values()) {
                if (s.styleName.equals(styleName)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Cannot find ParameterParser.Style matching style name " + styleName);
        }

        private String styleName = name().toLowerCase(Locale.ENGLISH);

        Style() {}

        Style(String styleName) {
            this.styleName = styleName;
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
    static Builder builder(String paramName, Location location, Style style) {
        return ParameterParserImpl.builder(paramName, location, style);
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

}
