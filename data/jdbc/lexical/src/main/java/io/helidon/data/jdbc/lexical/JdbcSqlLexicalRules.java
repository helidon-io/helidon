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

import io.helidon.common.Api;

/**
 * Shared delimiter rules used by JDBC SQL scanners.
 */
@Api.Internal
public final class JdbcSqlLexicalRules {

    private JdbcSqlLexicalRules() {
    }

    /**
     * Tests whether two dashes form a portable line-comment delimiter.
     * <p>
     * The second dash must be followed by whitespace, a control character, or
     * the end of the SQL source. A caller encountering two dashes must reject
     * the sequence when this method returns {@code false}; treating it as
     * ordinary SQL would have database-dependent semantics.
     *
     * @param source SQL source
     * @param start first dash offset
     * @return whether the dashes begin a portable line comment
     */
    public static boolean lineComment(String source, int start) {
        int contentStart = start + 2;
        if (contentStart == source.length()) {
            return true;
        }
        char next = source.charAt(contentStart);
        return Character.isWhitespace(next) || Character.isISOControl(next);
    }

    /**
     * Recognizes a PostgreSQL dollar quote delimiter at a token boundary.
     * <p>
     * An empty tag is valid. A named tag starts with a letter or underscore
     * and continues with letters, digits, or underscores.
     *
     * @param source SQL source
     * @param start candidate opening dollar offset
     * @return complete delimiter, or {@code null} when the candidate is not a delimiter
     */
    public static String dollarDelimiter(String source, int start) {
        if (start > 0 && identifierContinuation(source.charAt(start - 1))) {
            return null;
        }
        int index = start + 1;
        if (index >= source.length()) {
            return null;
        }
        char first = source.charAt(index);
        if (first == '$') {
            return "$$";
        }
        if (!Character.isLetter(first) && first != '_') {
            return null;
        }
        index++;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '$') {
                return source.substring(start, index + 1);
            }
            if (!Character.isLetterOrDigit(current) && current != '_') {
                return null;
            }
            index++;
        }
        return null;
    }

    /**
     * Recognizes an Oracle alternative quote opening delimiter at a token
     * boundary.
     *
     * @param source SQL source
     * @param start candidate {@code q} or {@code Q} offset
     * @return closing delimiter, or NUL when the candidate is not a valid opener
     */
    public static char qQuoteClosingDelimiter(String source, int start) {
        if (start > 0 && identifierContinuation(source.charAt(start - 1))) {
            return '\0';
        }
        if (start + 2 >= source.length()
                || (source.charAt(start) != 'q' && source.charAt(start) != 'Q')
                || source.charAt(start + 1) != '\'') {
            return '\0';
        }
        char opening = source.charAt(start + 2);
        if (opening == '\'' || Character.isWhitespace(opening) || Character.isISOControl(opening)) {
            return '\0';
        }
        return switch (opening) {
        case '[' -> ']';
        case '(' -> ')';
        case '{' -> '}';
        case '<' -> '>';
        default -> opening;
        };
    }

    private static boolean identifierContinuation(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '$';
    }
}
