/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;

final class AcceptQuery {
    private AcceptQuery() {
    }

    static List<HttpMediaType> parse(String value) {
        Objects.requireNonNull(value, "Accept-Query value must not be null");
        return new Parser(value).parse();
    }

    static String serialize(MediaType... mediaTypes) {
        Objects.requireNonNull(mediaTypes, "Accept-Query media types must not be null");

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < mediaTypes.length; i++) {
            MediaType mediaType = Objects.requireNonNull(mediaTypes[i], "Accept-Query media type must not be null");
            if (i > 0) {
                result.append(", ");
            }

            appendStringOrToken(result, mediaRange(mediaType));
            if (mediaType instanceof HttpMediaType httpMediaType) {
                for (Map.Entry<String, String> entry : httpMediaType.parameters().entrySet()) {
                    String key = entry.getKey().toLowerCase(Locale.ROOT);
                    validateKey(key);
                    result.append(';')
                            .append(key)
                            .append('=');
                    appendStringOrToken(result,
                                        Objects.requireNonNull(entry.getValue(),
                                                               "Accept-Query parameter value must not be null"));
                }
            }
        }
        return result.toString();
    }

    private static HttpMediaType createMediaType(String mediaRange, Map<String, String> parameters) {
        int slash = mediaRange.indexOf('/');
        if (slash < 1 || slash != mediaRange.lastIndexOf('/') || slash == mediaRange.length() - 1) {
            throw invalid("Invalid media range");
        }

        String type = mediaRange.substring(0, slash);
        String subtype = mediaRange.substring(slash + 1);
        validateMediaRange(type, subtype);
        return HttpMediaType.builder()
                .mediaType(MediaTypes.create(type, subtype))
                .parameters(parameters)
                .build();
    }

    private static String mediaRange(MediaType mediaType) {
        String type = Objects.requireNonNull(mediaType.type(), "Accept-Query media type must not be null");
        String subtype = Objects.requireNonNull(mediaType.subtype(), "Accept-Query media subtype must not be null");
        validateMediaRange(type, subtype);
        return type + "/" + subtype;
    }

    private static void validateMediaRange(String type, String subtype) {
        if (!HttpToken.isValid(type) || !HttpToken.isValid(subtype)) {
            throw invalid("Invalid media range");
        }
        if (type.indexOf('*') >= 0 && !("*".equals(type) && "*".equals(subtype))) {
            throw invalid("Unsupported wildcard media range");
        }
        if (subtype.indexOf('*') >= 0 && !"*".equals(subtype)) {
            throw invalid("Unsupported wildcard media range");
        }
    }

    private static void appendStringOrToken(StringBuilder result, String value) {
        if (isToken(value)) {
            result.append(value);
            return;
        }

        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 || ch >= 0x7f) {
                throw invalid("Value cannot be represented as a Structured Field String");
            }
            if (ch == '"' || ch == '\\') {
                result.append('\\');
            }
            result.append(ch);
        }
        result.append('"');
    }

    private static void validateKey(String key) {
        if (key.isEmpty() || !isKeyFirst(key.charAt(0))) {
            throw invalid("Invalid parameter name");
        }
        for (int i = 1; i < key.length(); i++) {
            if (!isKeyCharacter(key.charAt(i))) {
                throw invalid("Invalid parameter name");
            }
        }
    }

    private static boolean isToken(String value) {
        if (value.isEmpty() || !isTokenFirst(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!isTokenCharacter(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTokenFirst(char ch) {
        return isAlpha(ch) || ch == '*';
    }

    private static boolean isTokenCharacter(char ch) {
        return isAlpha(ch)
                || ch >= '0' && ch <= '9'
                || switch (ch) {
                    case '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~', ':', '/' -> true;
                    default -> false;
                };
    }

    private static boolean isKeyFirst(char ch) {
        return isLowerAlpha(ch) || ch == '*';
    }

    private static boolean isKeyCharacter(char ch) {
        return isLowerAlpha(ch)
                || ch >= '0' && ch <= '9'
                || ch == '_'
                || ch == '-'
                || ch == '.'
                || ch == '*';
    }

    private static boolean isAlpha(char ch) {
        return ch >= 'A' && ch <= 'Z' || isLowerAlpha(ch);
    }

    private static boolean isLowerAlpha(char ch) {
        return ch >= 'a' && ch <= 'z';
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid Accept-Query header: " + message);
    }

    private static final class Parser {
        private final String value;
        private int index;

        private Parser(String value) {
            this.value = value;
        }

        private List<HttpMediaType> parse() {
            skipSpaces();
            if (atEnd()) {
                return List.of();
            }

            List<HttpMediaType> result = new ArrayList<>();
            while (true) {
                result.add(parseMediaType());
                skipOptionalWhitespace();
                if (atEnd()) {
                    return List.copyOf(result);
                }
                require(',');
                skipOptionalWhitespace();
                if (atEnd()) {
                    throw invalid("Trailing comma");
                }
            }
        }

        private HttpMediaType parseMediaType() {
            String mediaRange = parseStringOrToken();
            Map<String, String> parameters = new LinkedHashMap<>();
            while (!atEnd() && current() == ';') {
                index++;
                skipSpaces();
                String key = parseKey();
                if (atEnd() || current() != '=') {
                    throw invalid("Accept-Query parameters must have String or Token values");
                }
                index++;
                parameters.put(key, parseStringOrToken());
            }
            return createMediaType(mediaRange, parameters);
        }

        private String parseStringOrToken() {
            if (atEnd()) {
                throw invalid("Expected a String or Token");
            }
            return current() == '"' ? parseString() : parseToken();
        }

        private String parseString() {
            index++;
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char ch = current();
                index++;
                if (ch == '\\') {
                    if (atEnd()) {
                        throw invalid("Incomplete String escape");
                    }
                    ch = current();
                    index++;
                    if (ch != '"' && ch != '\\') {
                        throw invalid("Invalid String escape");
                    }
                } else if (ch == '"') {
                    return result.toString();
                } else if (ch < 0x20 || ch >= 0x7f) {
                    throw invalid("Invalid String character");
                }
                result.append(ch);
            }
            throw invalid("Unterminated String");
        }

        private String parseToken() {
            if (!isTokenFirst(current())) {
                throw invalid("Expected a String or Token");
            }
            int start = index++;
            while (!atEnd() && isTokenCharacter(current())) {
                index++;
            }
            return value.substring(start, index);
        }

        private String parseKey() {
            if (atEnd() || !isKeyFirst(current())) {
                throw invalid("Invalid parameter name");
            }
            int start = index++;
            while (!atEnd() && isKeyCharacter(current())) {
                index++;
            }
            return value.substring(start, index);
        }

        private void skipSpaces() {
            while (!atEnd() && current() == ' ') {
                index++;
            }
        }

        private void skipOptionalWhitespace() {
            while (!atEnd() && (current() == ' ' || current() == '\t')) {
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
