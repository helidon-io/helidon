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
            if (current == '\'') {
                int start = index;
                ordinary();
                quoted('\'');
                protectedRegion(RegionKind.SINGLE_QUOTE, start);
            } else if (current == '"') {
                int start = index;
                ordinary();
                quoted('"');
                protectedRegion(RegionKind.DOUBLE_QUOTE, start);
            } else if (current == '`' && profile.backtickIdentifiers()) {
                int start = index;
                ordinary();
                quoted('`');
                protectedRegion(RegionKind.BACKTICK_IDENTIFIER, start);
            } else if (current == '[' && profile.bracketIdentifiers()) {
                int start = index;
                ordinary();
                bracketIdentifier();
                protectedRegion(RegionKind.BRACKET_IDENTIFIER, start);
            } else if (current == '-' && peek(1) == '-' && JdbcSqlLexicalRules.lineComment(source, index)) {
                int start = index;
                ordinary();
                lineComment();
                protectedRegion(RegionKind.LINE_COMMENT, start);
            } else if (current == '/' && peek(1) == '*') {
                int start = index;
                ordinary();
                blockComment();
                protectedRegion(RegionKind.BLOCK_COMMENT, start);
            } else if ((current == 'q' || current == 'Q')
                    && profile.qQuotedStrings()
                    && JdbcSqlLexicalRules.qQuoteClosingDelimiter(source, index) != '\0') {
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
        if (!Character.isJavaIdentifierStart(next)) {
            index++;
            return;
        }
        int start = index;
        int end = index + 2;
        while (end < length && Character.isJavaIdentifierPart(source.charAt(end))) {
            end++;
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

    private void quoted(char delimiter) {
        index++;
        while (index < length) {
            if (source.charAt(index) == delimiter) {
                if (peek(1) == delimiter) {
                    index += 2;
                } else {
                    index++;
                    return;
                }
            } else {
                index++;
            }
        }
        throw malformed("Unterminated quoted SQL region");
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

    private void alternativeQuoted() {
        char closing = JdbcSqlLexicalRules.qQuoteClosingDelimiter(source, index);
        index += 3;
        while (index + 1 < length) {
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
