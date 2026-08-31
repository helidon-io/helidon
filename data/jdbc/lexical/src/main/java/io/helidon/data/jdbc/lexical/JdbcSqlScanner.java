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
package io.helidon.data.jdbc.lexical;

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.data.jdbc.lexical.JdbcSqlScanHandler.RegionKind;

/**
 * Scans JDBC SQL for protected regions and bind markers without parsing SQL
 * grammar.
 */
@Api.Internal
public final class JdbcSqlScanner {

    private final String source;
    private final int length;
    private final JdbcSqlLexicalProfile profile;
    private final JdbcSqlScanHandler handler;
    private int index;
    private int ordinaryStart;

    private JdbcSqlScanner(String source,
                           JdbcSqlLexicalProfile profile,
                           JdbcSqlScanHandler handler) {
        this.source = source;
        this.length = source.length();
        this.profile = profile;
        this.handler = handler;
    }

    /**
     * Scans one SQL source and reports its regions in encounter order.
     *
     * @param source SQL source
     * @param profile lexical rules to apply
     * @param handler receiver for regions and markers
     * @throws IllegalArgumentException when a protected region is malformed
     */
    public static void scan(String source,
                            JdbcSqlLexicalProfile profile,
                            JdbcSqlScanHandler handler) {
        Objects.requireNonNull(source, "The SQL source must not be null.");
        Objects.requireNonNull(profile, "The JDBC SQL lexical profile must not be null.");
        Objects.requireNonNull(handler, "The JDBC SQL scan handler must not be null.");
        new JdbcSqlScanner(source, profile, handler).scan();
    }

    private void scan() {
        while (index < length) {
            char current = source.charAt(index);
            if ((current == 'e' || current == 'E')
                    && peek(1) == '\''
                    && (index == 0 || !Character.isJavaIdentifierPart(source.codePointBefore(index)))) {
                int start = index;
                ordinary();
                // PostgreSQL makes backslash significant only when E prefixes the literal. Keeping that decision at
                // the opener preserves standard doubled-quote behavior for every ordinary single-quoted string.
                index++;
                quoted('\'', true);
                protectedRegion(RegionKind.SINGLE_QUOTE, start);
            } else if (current == '\'') {
                int start = index;
                ordinary();
                quoted('\'', false);
                protectedRegion(RegionKind.SINGLE_QUOTE, start);
            } else if (current == '"') {
                int start = index;
                ordinary();
                quoted('"', false);
                protectedRegion(RegionKind.DOUBLE_QUOTE, start);
            } else if (current == '`' && profile.backtickIdentifiers()) {
                int start = index;
                ordinary();
                quoted('`', false);
                protectedRegion(RegionKind.BACKTICK_IDENTIFIER, start);
            } else if (current == '[' && profile.bracketIdentifiers()) {
                int start = index;
                ordinary();
                bracketIdentifier();
                protectedRegion(RegionKind.BRACKET_IDENTIFIER, start);
            } else if (current == '-' && peek(1) == '-') {
                if (!JdbcSqlLexicalRules.lineComment(source, index)) {
                    // Databases disagree whether a no-whitespace double dash is subtraction or a comment. Rejecting
                    // it keeps marker recognition deterministic instead of inspecting text a driver may ignore.
                    throw malformed("Ambiguous double-dash SQL sequence; place whitespace after the second dash for "
                                             + "a comment or separate consecutive subtraction operators");
                }
                int start = index;
                ordinary();
                lineComment();
                protectedRegion(RegionKind.LINE_COMMENT, start);
            } else if (current == '/' && peek(1) == '*') {
                int start = index;
                ordinary();
                blockComment();
                protectedRegion(RegionKind.BLOCK_COMMENT, start);
            } else if ((current == 'q' || current == 'Q' || current == 'n' || current == 'N')
                    && profile.qQuotedStrings()
                    && JdbcSqlLexicalRules.qQuoteClosingDelimiter(source, index) != '\0') {
                // Oracle prefixes are case-insensitive. Starting at n lets the lexical rule test the boundary before
                // the complete nq prefix; starting at q would incorrectly treat the prefix's n as identifier text.
                // It also keeps the n inside the protected region so handlers preserve the complete Oracle literal.
                int start = index;
                ordinary();
                alternativeQuoted();
                protectedRegion(RegionKind.ALTERNATIVE_QUOTE, start);
            } else if (current == '$' && profile.dollarQuotedStrings()) {
                String delimiter = JdbcSqlLexicalRules.dollarDelimiter(source, index);
                if (delimiter == null) {
                    index++;
                } else {
                    int start = index;
                    ordinary();
                    dollarQuoted(delimiter);
                    protectedRegion(RegionKind.DOLLAR_QUOTE, start);
                }
            } else if (current == ':') {
                namedMarker();
            } else if (current == '?') {
                positionalMarker();
            } else {
                index++;
            }
        }
        ordinary();
    }

    private void namedMarker() {
        char next = peek(1);
        if (next == ':' || next == '=') {
            index += 2;
            return;
        }

        int start = index;
        int nameStart = start + 1;
        if (nameStart >= length) {
            index++;
            return;
        }

        // Handler offsets are UTF-16 String indices. Classify complete Unicode code points but advance by their
        // UTF-16 width, preserving the source spelling and callback contract without normalization. The accepted
        // identifier set intentionally follows the Java identifier rules of the JDK
        int codePoint = source.codePointAt(nameStart);
        if (!Character.isJavaIdentifierStart(codePoint)) {
            index++;
            return;
        }
        int end = nameStart + Character.charCount(codePoint);
        while (end < length) {
            codePoint = source.codePointAt(end);
            if (!Character.isJavaIdentifierPart(codePoint)) {
                break;
            }
            end += Character.charCount(codePoint);
        }
        ordinary();
        handler.namedMarker(start, end);
        index = end;
        ordinaryStart = end;
    }

    private void positionalMarker() {
        if (peek(1) == '?' && profile.questionMarkEscape()) {
            index += 2;
            return;
        }
        int offset = index;
        ordinary();
        handler.positionalMarker(offset);
        index++;
        ordinaryStart = index;
    }

    private void quoted(char delimiter, boolean postgresqlEscapeString) {
        while (true) {
            index++;
            boolean closed = false;
            while (index < length) {
                char current = source.charAt(index);
                if (postgresqlEscapeString && current == '\\') {
                    // A backslash protects the next character in a PostgreSQL escape string, including a quote.
                    index++;
                    if (index < length) {
                        index++;
                    }
                } else if (current == delimiter) {
                    if (peek(1) == delimiter) {
                        index += 2;
                    } else {
                        index++;
                        closed = true;
                        break;
                    }
                } else {
                    index++;
                }
            }
            if (!closed) {
                throw malformed("Unterminated quoted SQL region");
            }
            if (!postgresqlEscapeString) {
                return;
            }

            int continuation = index;
            boolean lineBreak = false;
            while (continuation < length && Character.isWhitespace(source.charAt(continuation))) {
                char whitespace = source.charAt(continuation++);
                lineBreak |= whitespace == '\n' || whitespace == '\r';
            }
            if (!lineBreak || continuation >= length || source.charAt(continuation) != '\'') {
                return;
            }

            // PostgreSQL permits newline-separated escape-string segments without repeating the E prefix.
            index = continuation;
        }
    }

    private void bracketIdentifier() {
        index++;
        while (index < length) {
            if (source.charAt(index) == ']') {
                if (peek(1) == ']') {
                    index += 2;
                } else {
                    index++;
                    return;
                }
            } else {
                index++;
            }
        }
        throw malformed("Unterminated bracket quoted identifier");
    }

    private void lineComment() {
        index += 2;
        while (index < length) {
            char current = source.charAt(index++);
            if (current == '\n' || current == '\r') {
                return;
            }
        }
    }

    private void blockComment() {
        index += 2;
        int depth = 1;
        while (index < length) {
            if (source.charAt(index) == '/' && peek(1) == '*') {
                if (!profile.nestedBlockComments()) {
                    throw malformed("Nested block comments are not supported");
                }
                depth++;
                index += 2;
            } else if (source.charAt(index) == '*' && peek(1) == '/') {
                depth--;
                index += 2;
                if (depth == 0) {
                    return;
                }
            } else {
                index++;
            }
        }
        throw malformed("Unterminated block comment");
    }

    /**
     * Advances over one complete Oracle alternative-quoted literal.
     * <p>
     * The caller has already verified that {@link #index} begins a valid
     * {@code q} or {@code nq} opener. On success this method leaves the index
     * immediately after the closing delimiter and apostrophe, allowing the
     * outer scan to resume with ordinary SQL. Apostrophes, question marks, and
     * named-marker-shaped text inside the literal remain opaque to marker
     * recognition. An opener without its matching delimiter and apostrophe is
     * rejected instead of allowing the scanner to reinterpret part of the
     * literal as ordinary SQL.
     *
     * @throws IllegalArgumentException when the alternative-quoted literal is unterminated
     */
    private void alternativeQuoted() {
        char closing = JdbcSqlLexicalRules.qQuoteClosingDelimiter(source, index);
        boolean national = source.charAt(index) == 'n' || source.charAt(index) == 'N';
        // Skip q'<delimiter> or nq'<delimiter> so the search begins with the literal's content.
        index += national ? 4 : 3;
        while (index + 1 < length) {
            // Oracle closes the literal only when the matching delimiter is immediately followed by an apostrophe.
            if (source.charAt(index) == closing && source.charAt(index + 1) == '\'') {
                index += 2;
                return;
            }
            index++;
        }
        throw malformed("Unterminated alternative quoted string");
    }

    private void dollarQuoted(String delimiter) {
        int contentEnd = source.indexOf(delimiter, index + delimiter.length());
        if (contentEnd < 0) {
            throw malformed("Unterminated dollar quoted string");
        }
        index = contentEnd + delimiter.length();
    }

    private void ordinary() {
        if (ordinaryStart < index) {
            handler.ordinary(ordinaryStart, index);
        }
    }

    private void protectedRegion(RegionKind kind, int start) {
        handler.protectedRegion(kind, start, index);
        ordinaryStart = index;
    }

    private char peek(int offset) {
        int target = index + offset;
        return target < length ? source.charAt(target) : '\0';
    }

    private IllegalArgumentException malformed(String problem) {
        return new JdbcSqlLexicalException(problem, profile, index);
    }
}
