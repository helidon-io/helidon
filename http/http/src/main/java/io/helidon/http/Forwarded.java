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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.helidon.common.uri.UriValidator;

import static io.helidon.http.HeaderNames.FORWARDED;

/**
 * A representation of the {@link HeaderNames#FORWARDED} HTTP header.
 */
public class Forwarded {
    private static final System.Logger LOGGER = System.getLogger(Forwarded.class.getName());
    private final Optional<String> by;
    private final Optional<String> forClient;
    private final Optional<String> host;
    private final Optional<String> proto;

    private Forwarded(String by, String forClient, String host, String proto) {
        this.by = Optional.ofNullable(by);
        this.forClient = Optional.ofNullable(forClient);
        this.host = Optional.ofNullable(host);
        this.proto = Optional.ofNullable(proto);
    }

    /**
     * Create forwarded from a value of a single forwarded header, such as {@code by=a.b.c;for=d.e.f;host=host;proto=https}.
     *
     * @param string string representation of a single forwarded header
     * @return forwarded parsed from the string
     * @see #create(Headers)
     */
    public static Forwarded create(String string) {
        // first split by semicolon, as that separates the directives
        String by = null;
        String forClient = null;
        String host = null;
        String proto = null;

        for (String directive : split(string, ';')) {
            if (directive.isEmpty()) {
                continue;
            }
            int index = directive.indexOf('=');
            if (index == -1) {
                throw new IllegalArgumentException("Invalid Forwarded header");
            }
            String name = directive.substring(0, index);
            String value = unquote(directive.substring(index + 1));

            switch (name.toLowerCase(Locale.ROOT)) {
            case "by" -> {
                by = value;
            }
            case "for" -> {
                forClient = value;
            }
            case "host" -> {
                validateHost(value);
                host = value;
            }
            case "proto" -> {
                proto = value;
            }
            default -> {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    String printableName = name.replaceAll("\\p{C}", "?");
                    String printableValue = value.replaceAll("\\p{C}", "?");

                    LOGGER.log(System.Logger.Level.DEBUG, "Encountered unknown directive of Forwarded header: \n"
                            + printableName + "\nValue:\n" + printableValue);
                }
            }
            }
        }
        return new Forwarded(by, forClient, host, proto);
    }

    /**
     * Parse forwarded header(s) from the provided headers.
     *
     * @param headers header to process
     * @return list of forwarded headers, will be empty if the header does not exist.
     */
    public static List<Forwarded> create(Headers headers) {
        List<String> values = headers.all(FORWARDED, List::of);
        return values.isEmpty() ? List.of() : values.stream()
                    .flatMap(it -> split(it, ',').stream())
                    .map(String::trim)
                    .filter(it -> !it.isEmpty())
                    .map(Forwarded::create)
                    .toList();
    }

    static int hostPortDelimiter(String authority) {
        if (!authority.startsWith("[")) {
            return authority.lastIndexOf(':');
        }
        int closingBracket = authority.lastIndexOf(']');
        if (closingBracket < 0) {
            throw new IllegalArgumentException("Invalid Forwarded header");
        }
        if (closingBracket + 1 == authority.length()) {
            return -1;
        }
        if (authority.charAt(closingBracket + 1) == ':') {
            return closingBracket + 1;
        }
        throw new IllegalArgumentException("Invalid Forwarded header");
    }

    /**
     * {@code by} directive of the forwarded header.
     *
     * @return by directive
     */
    public Optional<String> by() {
        return by;
    }

    /**
     * {@code for} directive of the forwarded header.
     *
     * @return for directive
     */
    public Optional<String> forClient() {
        return forClient;
    }

    /**
     * {@code host} directive of the forwarded header. The host of the original request.
     *
     * @return host directive
     */
    public Optional<String> host() {
        return host;
    }

    /**
     * {@code proto} directive of the forwarded header. The protocol of the original request (http or https).
     *
     * @return proto directive
     */
    public Optional<String> proto() {
        return proto;
    }

    private static void validateHost(String authority) {
        int portDelimiter = hostPortDelimiter(authority);
        String host = portDelimiter < 0 ? authority : authority.substring(0, portDelimiter);

        try {
            UriValidator.validateHost(host);
            if (portDelimiter >= 0) {
                String port = authority.substring(portDelimiter + 1);
                for (int i = 0; i < port.length(); i++) {
                    char c = port.charAt(i);
                    if (c < '0' || c > '9') {
                        throw new IllegalArgumentException("Port is not numeric");
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Forwarded header", e);
        }
    }

    private static List<String> split(String string, char delimiter) {
        List<String> result = new ArrayList<>();
        int start = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (quoted && c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == delimiter) {
                result.add(string.substring(start, i));
                start = i + 1;
            }
        }
        if (quoted || escaped) {
            throw new IllegalArgumentException("Invalid Forwarded header");
        }
        result.add(string.substring(start));
        return result;
    }

    private static String unquote(String string) {
        int lastIndex = string.length() - 1;
        if (string.indexOf('"') == 0 && string.lastIndexOf('"') == lastIndex) {
            int escapeIndex = string.indexOf('\\', 1);
            if (escapeIndex < 0) {
                return string.substring(1, lastIndex);
            }
            StringBuilder result = new StringBuilder(lastIndex - 1);
            result.append(string, 1, escapeIndex);
            for (int i = escapeIndex; i < lastIndex; i++) {
                char c = string.charAt(i);
                result.append(c == '\\' ? string.charAt(++i) : c);
            }
            return result.toString();
        }
        return string;
    }
}
