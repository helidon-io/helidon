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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsers for OpenAPI parameter styles. Includes a factory method.
 * <p>
 * In OpenAPI, parameters are formatted according to a subset of
 * {@link https://tools.ietf.org/html/rfc6570 RFC 6570}, using several different
 * styles. Which styles are valid depends on where the parameter appears -- its
 * location -- (header, path, query parameter, cookie). This class implements
 * each supported parameter style as separate parser, and it includes a factory
 * method which returns an instance of the correct parser given the parameter's
 * location and style.
 */
public abstract class ParameterParser {

    public static class Builder {

        private Optional<Location> location = Optional.empty();
        private Optional<Style> style = Optional.empty();
        private Optional<Boolean> explode = Optional.empty();

        public Builder location(String location) {
            this.location = Optional.of(Location.match(location));
            return this;
        }

        public Builder style(String style) {
            this.style = Optional.of(Style.match(style));
            return this;
        }

        public Builder explode(boolean explode) {
            this.explode = Optional.of(explode);
            return this;
        }

        public ParameterParser build() {
            if (!location.isPresent()) {
                throw new IllegalArgumentException("ParameterParser must specify Location");
            }
            if (!location().supportsStyle(style())) {
                throw new IllegalArgumentException("Location " + location().name()
                + " does not support style " + style().name());
            }
            return style().factory.apply(explode());
        }

        private Location location() {
            return location.get();
        }

        private Style style() {
            return style.orElse(location().defaultStyle);
        }

        private boolean explode() {
            return explode.orElse(location().defaultExplode);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final boolean explode;

    private ParameterParser(boolean explode) {
        this.explode = explode;
    }

    boolean explode() {
        return explode;
    }

    public abstract List<String> parse(String value);

    public List<String> parse(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String v : values) {
            result.addAll(parse(v));
        }
        return result;
    }



    /**
     * Locations in a request where parameters might be found.
     * <p>
     * Each parameter location has a default {@code Style}, a default {@code explode}
     * setting, and a collection of {@code Style}s valid for that location.
     */
    static enum Location {
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

        static Location match(String locationName) {
            return Enum.valueOf(Location.class, locationName.toUpperCase(Locale.ENGLISH));
        }
    }

    static enum Style {
        SIMPLE(SimpleParser::new),
        LABEL(LabelParser::new),
        MATRIX(MatrixParser::new),
        FORM(FormParser::new),
        SPACE_DELIMITED(SpaceDelimitedParser::new, "spaceDelimited"),
        PIPE_DELIMITED(PipeDelimitedParser::new, "pipeDelimited"),
        DEEP_OBJECT(DeepObjectParser::new, "deepObject");

        static Style match(String styleName) {
            for (Style s : Style.values()) {
                if (s.styleName.equals(styleName)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Cannot find ParameterParser.Style matching style name " + styleName);
        }

        private final String styleName;
        private final Function<Boolean, ParameterParser> factory;

        Style(Function<Boolean, ParameterParser> factory) {
            this.factory = factory;
            styleName = name().toLowerCase(Locale.ENGLISH);
        }

        Style(Function<Boolean, ParameterParser> factory, String styleName) {
            this.factory = factory;
            this.styleName = styleName;
        }

        ParameterParser parser(boolean explode) {
            return factory.apply(explode);
        }
    }

    static class SimpleParser extends DelimitedParser {

        SimpleParser(boolean explode) {
            super(",", explode);
        }
    }

    static class LabelParser extends PrefixedParser {

        LabelParser(boolean explode) {
            super(".", explode);
        }
    }

    static class MatrixParser extends PrefixedParser {

        MatrixParser(boolean explode) {
            super(";", explode);
        }
    }

    static class FormParser extends DelimitedParser {

        FormParser(boolean explode) {
            super("&", explode);
        }
    }

    static class SpaceDelimitedParser extends DelimitedParser {

        SpaceDelimitedParser(boolean explode) {
            super(" ", explode);
        }
    }

    static class PipeDelimitedParser extends DelimitedParser {

        PipeDelimitedParser(boolean explode) {
            super("\\|", explode);
        }
    }

    static class DeepObjectParser extends ParameterParser {

        DeepObjectParser(boolean explode) {
            super(explode);
        }

        @Override
        public List<String> parse(String value) {
            final List<String> result = new ArrayList<>();
            return result;
        }


    }

    static class DelimitedParser extends ParameterParser {

        private final String delimiter;

        DelimitedParser(String delimiter, boolean explode) {
            super(explode);
            this.delimiter = delimiter;
        }

        @Override
        public List<String> parse(String value) {
            return Arrays.asList(value.split(delimiter));
        }
    }

    static class PrefixedParser extends ParameterParser {

        private final String prefix;
        private final Pattern pattern;

        PrefixedParser(String prefix, boolean explode) {
            super(explode);
            this.prefix = prefix;
            pattern = Pattern.compile("\\" + prefix + "([^" + "\\" + prefix + "]+)");
        }

        @Override
        public List<String> parse(String value) {
            final List<String> result = new ArrayList<>();
            final Matcher matcher = pattern.matcher(value);
            while (matcher.find()) {
                result.add(matcher.group(1));
            }
            return result;
        }
    }
}
