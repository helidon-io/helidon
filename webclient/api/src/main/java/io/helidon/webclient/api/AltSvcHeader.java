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

package io.helidon.webclient.api;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.uri.UriValidator;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.DateTime;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpToken;

/**
 * Parsed, protocol-neutral {@code Alt-Svc} response header.
 */
@Api.Internal
public final class AltSvcHeader {
    private static final long DEFAULT_MAX_AGE_SECONDS = Duration.ofHours(24).toSeconds();
    private static final int MAX_ALTERNATIVES = 32;
    private static final int MAX_EMPTY_LIST_ELEMENTS = 32;
    private static final int MAX_PROTOCOL_ID_LENGTH = 0xFF;
    private static final int MAX_ENCODED_PROTOCOL_ID_LENGTH = MAX_PROTOCOL_ID_LENGTH * 3;
    private static final AltSvcHeader CLEAR = new AltSvcHeader(true, List.of());
    private static final GrammarResult CLEAR_GRAMMAR = new GrammarResult(true, false, List.of());
    private static final GrammarResult MALFORMED_GRAMMAR = new GrammarResult(false, true, List.of());

    private static volatile GrammarMemo grammarMemo;
    private static volatile DateMemo dateMemo;

    private final boolean clear;
    private final List<Alternative> alternatives;

    private AltSvcHeader(boolean clear, List<Alternative> alternatives) {
        this.clear = clear;
        this.alternatives = List.copyOf(alternatives);
    }

    /**
     * Create an {@code Alt-Svc} update by parsing all field lines atomically.
     * Up to {@value #MAX_EMPTY_LIST_ELEMENTS} empty comma-list elements are ignored across all field lines, as required
     * by RFC 9110's list-extension rules. Additional empty elements make the update malformed.
     *
     * @param headers response headers
     * @param receivedAt time the response headers were received
     * @return parsed update, or empty when the field is absent or malformed
     */
    public static Optional<AltSvcHeader> create(ClientResponseHeaders headers, Instant receivedAt) {
        ClientResponseHeaders checkedHeaders = Objects.requireNonNull(headers, "headers");
        Instant responseTime = Objects.requireNonNull(receivedAt, "receivedAt");
        if (!checkedHeaders.contains(HeaderNames.ALT_SVC)) {
            return Optional.empty();
        }

        Header header = checkedHeaders.get(HeaderNames.ALT_SVC);
        GrammarResult grammar = parseGrammar(header);
        if (grammar.clear()) {
            return Optional.of(CLEAR);
        }
        if (grammar.malformed()) {
            return Optional.empty();
        }

        long responseAge = responseAge(checkedHeaders, responseTime);
        List<AlternativeTemplate> templates = grammar.templates();
        if (templates.size() == 1) {
            AlternativeTemplate template = templates.getFirst();
            long freshFor = template.maxAge() > responseAge ? template.maxAge() - responseAge : 0;
            Alternative alternative = new Alternative(template.protocolId(),
                                                       template.host(),
                                                       template.port(),
                                                       expirationTime(responseTime, freshFor),
                                                       template.persist());
            return Optional.of(new AltSvcHeader(false, List.of(alternative)));
        }
        List<Alternative> alternatives = new ArrayList<>(templates.size());
        for (int index = 0; index < templates.size(); index++) {
            AlternativeTemplate template = templates.get(index);
            long freshFor = template.maxAge() > responseAge ? template.maxAge() - responseAge : 0;
            alternatives.add(new Alternative(template.protocolId(),
                                             template.host(),
                                             template.port(),
                                             expirationTime(responseTime, freshFor),
                                             template.persist()));
        }
        return Optional.of(new AltSvcHeader(false, alternatives));
    }

    /**
     * Whether this update clears alternatives previously learned for the origin.
     *
     * @return whether this is a clear update
     */
    public boolean clear() {
        return clear;
    }

    /**
     * Alternatives in received field order.
     *
     * @return immutable alternatives
     */
    public List<Alternative> alternatives() {
        return alternatives;
    }

    private static GrammarResult parseGrammar(Header header) {
        GrammarMemo memo = grammarMemo;
        List<String> fieldLineSnapshot;
        if (header.valueCount() == 1) {
            String fieldLine = header.get();
            if (memo != null && memo.fieldLines().size() == 1 && memo.fieldLines().getFirst().equals(fieldLine)) {
                return memo.result();
            }
            fieldLineSnapshot = List.of(fieldLine);
        } else {
            List<String> fieldLines = header.allValues();
            if (memo != null && memo.fieldLines().equals(fieldLines)) {
                return memo.result();
            }
            fieldLineSnapshot = List.copyOf(fieldLines);
        }

        GrammarResult result = parseGrammarSnapshot(fieldLineSnapshot);
        grammarMemo = new GrammarMemo(fieldLineSnapshot, result);
        return result;
    }

    private static GrammarResult parseGrammarSnapshot(List<String> fieldLines) {
        List<String> encodedAlternatives = new ArrayList<>();
        boolean malformed = false;
        boolean tooMany = false;
        int emptyElements = 0;
        for (String headerValue : fieldLines) {
            AlternativeListResult result = splitAlternatives(headerValue, encodedAlternatives, emptyElements);
            if (result.clear()) {
                return CLEAR_GRAMMAR;
            }
            malformed |= result.malformed();
            tooMany |= result.tooMany();
            emptyElements = result.emptyElements();
        }
        if (malformed || tooMany || encodedAlternatives.isEmpty()) {
            return MALFORMED_GRAMMAR;
        }

        List<AlternativeTemplate> alternatives = new ArrayList<>(encodedAlternatives.size());
        for (String encodedAlternative : encodedAlternatives) {
            Optional<AlternativeTemplate> alternative = parseAlternative(encodedAlternative);
            if (alternative.isEmpty()) {
                return MALFORMED_GRAMMAR;
            }
            alternatives.add(alternative.get());
        }
        return new GrammarResult(false, false, List.copyOf(alternatives));
    }

    private static long responseAge(ClientResponseHeaders headers, Instant receivedAt) {
        long responseAge = 0;
        if (headers.contains(HeaderNames.AGE)) {
            long parsedAge = parseDeltaSeconds(headers.get(HeaderNames.AGE).get());
            if (parsedAge >= 0) {
                responseAge = parsedAge;
            }
        }
        if (headers.contains(HeaderNames.DATE)) {
            Instant responseDate = parseDate(headers.get(HeaderNames.DATE).get());
            if (responseDate != null && responseDate.isBefore(receivedAt)) {
                responseAge = Math.max(responseAge, elapsedSeconds(responseDate, receivedAt));
            }
        }
        return responseAge;
    }

    private static Instant parseDate(String value) {
        DateMemo memo = dateMemo;
        if (memo != null && memo.value().equals(value)) {
            return memo.parsedDate();
        }

        Instant parsedDate;
        try {
            parsedDate = DateTime.parse(value).toInstant();
        } catch (DateTimeParseException _) {
            parsedDate = null;
        }
        dateMemo = new DateMemo(value, parsedDate);
        return parsedDate;
    }

    private static long elapsedSeconds(Instant start, Instant end) {
        try {
            return Duration.between(start, end).toSeconds();
        } catch (ArithmeticException _) {
            return Long.MAX_VALUE;
        }
    }

    private static Optional<AlternativeTemplate> parseAlternative(String alternative) {
        String trimmed = trimOws(alternative);
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        int equals = trimmed.indexOf('=');
        if (equals < 1) {
            return Optional.empty();
        }
        Optional<String> protocolId = decodeProtocolId(trimmed.substring(0, equals));
        if (protocolId.isEmpty()) {
            return Optional.empty();
        }

        int quoteStart = equals + 1;
        Optional<QuotedValue> quotedAuthority = parseQuotedValue(trimmed, quoteStart);
        if (quotedAuthority.isEmpty()) {
            return Optional.empty();
        }
        Optional<AlternativeAuthority> authority = parseAuthority(quotedAuthority.get().value());
        if (authority.isEmpty()) {
            return Optional.empty();
        }

        long maxAge = DEFAULT_MAX_AGE_SECONDS;
        boolean persist = false;
        String parameterText = trimOws(trimmed.substring(quotedAuthority.get().endIndex() + 1));
        if (!parameterText.isEmpty()) {
            if (parameterText.charAt(0) != ';') {
                return Optional.empty();
            }
            Optional<List<String>> parameters = splitValues(parameterText.substring(1), ';');
            if (parameters.isEmpty()) {
                return Optional.empty();
            }
            boolean maxAgeSeen = false;
            for (String parameter : parameters.get()) {
                String value = trimOws(parameter);
                int parameterEquals = value.indexOf('=');
                if (parameterEquals < 1 || parameterEquals + 1 >= value.length()) {
                    return Optional.empty();
                }
                String name = value.substring(0, parameterEquals);
                String parameterValue = value.substring(parameterEquals + 1);
                Optional<String> decodedParameter = parseParameterValue(parameterValue);
                if (!validToken(name) || decodedParameter.isEmpty()) {
                    return Optional.empty();
                }
                String decodedValue = decodedParameter.get();
                if ("ma".equalsIgnoreCase(name)) {
                    if (maxAgeSeen) {
                        return Optional.empty();
                    }
                    maxAge = parseDeltaSeconds(decodedValue);
                    if (maxAge < 0) {
                        return Optional.empty();
                    }
                    maxAgeSeen = true;
                } else if ("persist".equalsIgnoreCase(name) && "1".equals(decodedValue)) {
                    persist = true;
                }
            }
        }

        AlternativeAuthority parsedAuthority = authority.get();
        return Optional.of(new AlternativeTemplate(protocolId.get(),
                                                   parsedAuthority.host(),
                                                   parsedAuthority.port(),
                                                   maxAge,
                                                   persist));
    }

    private static Optional<String> decodeProtocolId(String encodedProtocolId) {
        if (encodedProtocolId.length() > MAX_ENCODED_PROTOCOL_ID_LENGTH || !validToken(encodedProtocolId)) {
            return Optional.empty();
        }
        byte[] protocolId = new byte[Math.min(encodedProtocolId.length(), MAX_PROTOCOL_ID_LENGTH)];
        int protocolIdLength = 0;
        for (int index = 0; index < encodedProtocolId.length(); index++) {
            if (protocolIdLength == MAX_PROTOCOL_ID_LENGTH) {
                return Optional.empty();
            }
            char character = encodedProtocolId.charAt(index);
            int decodedByte = character;
            if (character == '%') {
                if (index + 2 >= encodedProtocolId.length()) {
                    return Optional.empty();
                }
                char highCharacter = encodedProtocolId.charAt(index + 1);
                char lowCharacter = encodedProtocolId.charAt(index + 2);
                if (!uppercaseHex(highCharacter) || !uppercaseHex(lowCharacter)) {
                    return Optional.empty();
                }
                int highNibble = Character.digit(highCharacter, 16);
                int lowNibble = Character.digit(lowCharacter, 16);
                decodedByte = (highNibble << 4) | lowNibble;
                if (decodedByte != '%' && tokenByte(decodedByte)) {
                    return Optional.empty();
                }
                index += 2;
            }
            protocolId[protocolIdLength++] = (byte) decodedByte;
        }
        if (protocolIdLength == 0) {
            return Optional.empty();
        }
        return Optional.of(new String(protocolId, 0, protocolIdLength, StandardCharsets.ISO_8859_1));
    }

    private static boolean uppercaseHex(char character) {
        return character >= '0' && character <= '9' || character >= 'A' && character <= 'F';
    }

    private static boolean tokenByte(int value) {
        return (value >= '0' && value <= '9')
                || (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || switch (value) {
                    case '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~' -> true;
                    default -> false;
                };
    }

    private static Optional<AlternativeAuthority> parseAuthority(String authority) {
        if (authority.isEmpty()) {
            return Optional.empty();
        }

        String host;
        String portPart;
        if (authority.startsWith(":")) {
            host = null;
            portPart = authority.substring(1);
        } else if (authority.startsWith("[")) {
            int closingBracket = authority.indexOf(']');
            if (closingBracket < 0
                    || closingBracket + 2 > authority.length()
                    || authority.charAt(closingBracket + 1) != ':') {
                return Optional.empty();
            }
            host = authority.substring(1, closingBracket);
            if (host.isEmpty()) {
                return Optional.empty();
            }
            if (!validHost(authority.substring(0, closingBracket + 1))) {
                return Optional.empty();
            }
            portPart = authority.substring(closingBracket + 2);
        } else {
            int colon = authority.lastIndexOf(':');
            if (colon < 1 || colon + 1 >= authority.length()) {
                return Optional.empty();
            }
            host = authority.substring(0, colon);
            if (host.indexOf(':') >= 0 || containsWhitespace(host) || !validHost(host)) {
                return Optional.empty();
            }
            portPart = authority.substring(colon + 1);
        }

        long port = parseDeltaSeconds(portPart);
        if (port < 1 || port > 65535) {
            return Optional.empty();
        }
        return Optional.of(new AlternativeAuthority(host, (int) port));
    }

    private static boolean validHost(String host) {
        try {
            UriValidator.validateHost(host);
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> parseParameterValue(String value) {
        if (!value.startsWith("\"")) {
            return validToken(value) ? Optional.of(value) : Optional.empty();
        }
        Optional<QuotedValue> quotedValue = parseQuotedValue(value, 0);
        if (quotedValue.isEmpty() || quotedValue.get().endIndex() + 1 != value.length()) {
            return Optional.empty();
        }
        return Optional.of(quotedValue.get().value());
    }

    private static AlternativeListResult splitAlternatives(String value,
                                                           List<String> result,
                                                           int initialEmptyElements) {
        int elementStart = 0;
        int emptyElements = initialEmptyElements;
        boolean quoted = false;
        boolean escaped = false;
        boolean malformed = emptyElements > MAX_EMPTY_LIST_ELEMENTS;
        boolean tooMany = false;
        boolean clear = false;
        for (int index = 0; index <= value.length(); index++) {
            if (index < value.length()) {
                char character = value.charAt(index);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (quoted && character == '\\') {
                    escaped = true;
                    continue;
                }
                if (character == '"') {
                    quoted = !quoted;
                    continue;
                }
                if (character != ',' || quoted) {
                    continue;
                }
            } else if (quoted || escaped) {
                return new AlternativeListResult(clear, true, tooMany, emptyElements);
            }

            String element = trimOws(value.substring(elementStart, index));
            if (element.isEmpty()) {
                if (emptyElements <= MAX_EMPTY_LIST_ELEMENTS) {
                    emptyElements++;
                }
                if (emptyElements > MAX_EMPTY_LIST_ELEMENTS) {
                    malformed = true;
                }
            } else if ("clear".equals(element)) {
                clear = true;
            } else if (result.size() == MAX_ALTERNATIVES) {
                tooMany = true;
            } else {
                result.add(element);
            }
            elementStart = index + 1;
        }
        return new AlternativeListResult(clear, malformed, tooMany, emptyElements);
    }

    private static Optional<List<String>> splitValues(String value, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) {
                current.append(character);
                escaped = false;
                continue;
            }
            if (quoted && character == '\\') {
                current.append(character);
                escaped = true;
                continue;
            }
            if (character == '"') {
                quoted = !quoted;
            }
            if (character == delimiter && !quoted) {
                result.add(trimOws(current.toString()));
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (quoted || escaped) {
            return Optional.empty();
        }
        result.add(trimOws(current.toString()));
        for (String item : result) {
            if (item.isEmpty()) {
                return Optional.empty();
            }
        }
        return Optional.of(result);
    }

    private static String trimOws(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && ows(value.charAt(start))) {
            start++;
        }
        while (end > start && ows(value.charAt(end - 1))) {
            end--;
        }
        return start == 0 && end == value.length() ? value : value.substring(start, end);
    }

    private static boolean ows(char character) {
        return character == ' ' || character == '\t';
    }

    private static Optional<QuotedValue> parseQuotedValue(String value, int quoteStart) {
        if (quoteStart >= value.length() || value.charAt(quoteStart) != '"') {
            return Optional.empty();
        }
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int index = quoteStart + 1; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) {
                if (!quotedPair(character)) {
                    return Optional.empty();
                }
                result.append(character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                return Optional.of(new QuotedValue(result.toString(), index));
            } else if (!quotedText(character)) {
                return Optional.empty();
            } else {
                result.append(character);
            }
        }
        return Optional.empty();
    }

    private static boolean quotedText(char character) {
        return character == '\t'
                || character == ' '
                || character == '!'
                || (character >= 0x23 && character <= 0x5B)
                || (character >= 0x5D && character <= 0x7E)
                || (character >= 0x80 && character <= 0xFF);
    }

    private static boolean quotedPair(char character) {
        return character == '\t'
                || character == ' '
                || (character >= 0x21 && character <= 0x7E)
                || (character >= 0x80 && character <= 0xFF);
    }

    private static boolean validToken(String value) {
        if (value.isEmpty()) {
            return false;
        }
        try {
            HttpToken.validate(value);
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    private static long parseDeltaSeconds(String value) {
        if (value.isEmpty()) {
            return -1;
        }
        long result = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return -1;
            }
            int digit = character - '0';
            if (result > (Long.MAX_VALUE - digit) / 10) {
                return Long.MAX_VALUE;
            }
            result = result * 10 + digit;
        }
        return result;
    }

    private static Instant expirationTime(Instant receivedAt, long freshFor) {
        long maximumFreshness = Instant.MAX.getEpochSecond() - receivedAt.getEpochSecond();
        return receivedAt.plusSeconds(Math.min(freshFor, maximumFreshness));
    }

    /**
     * One parsed alternative service.
     */
    @Api.Internal
    public static final class Alternative {
        private final String protocolId;
        private final String host;
        private final int port;
        private final Instant expirationTime;
        private final boolean persist;

        private Alternative(String protocolId, String host, int port, Instant expirationTime, boolean persist) {
            this.protocolId = protocolId;
            this.host = host;
            this.port = port;
            this.expirationTime = expirationTime;
            this.persist = persist;
        }

        /**
         * Percent-decoded ALPN protocol identifier, mapped one octet to each ISO-8859-1 character.
         *
         * @return protocol identifier
         */
        public String protocolId() {
            return protocolId;
        }

        /**
         * Alternative host without IPv6 brackets, or empty to retain the origin host.
         *
         * @return optional alternative host
         */
        public Optional<String> host() {
            return Optional.ofNullable(host);
        }

        /**
         * Alternative port.
         *
         * @return alternative port
         */
        public int port() {
            return port;
        }

        /**
         * Time at which this alternative expires after accounting for response age.
         *
         * @return expiration time
         */
        public Instant expirationTime() {
            return expirationTime;
        }

        /**
         * Whether this alternative can persist across network changes.
         *
         * @return whether the alternative is persistent
         */
        public boolean persist() {
            return persist;
        }
    }

    private record AlternativeAuthority(String host, int port) {
    }

    private record AlternativeTemplate(String protocolId, String host, int port, long maxAge, boolean persist) {
    }

    private record AlternativeListResult(boolean clear, boolean malformed, boolean tooMany, int emptyElements) {
    }

    private record DateMemo(String value, Instant parsedDate) {
    }

    private record GrammarMemo(List<String> fieldLines, GrammarResult result) {
    }

    private record GrammarResult(boolean clear, boolean malformed, List<AlternativeTemplate> templates) {
    }

    private record QuotedValue(String value, int endIndex) {
    }
}
