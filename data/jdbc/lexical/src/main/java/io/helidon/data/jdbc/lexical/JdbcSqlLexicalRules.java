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
     * Recognizes an Oracle alternative-quoted literal opener at a token
     * boundary and returns the delimiter which closes its content.
     * <p>
     * Oracle accepts both the ordinary {@code q'<delimiter>} prefix and the
     * national-character {@code nq'<delimiter>} prefix without regard to
     * letter case. The {@code start} offset identifies the beginning of that
     * complete prefix: {@code q} for the ordinary form or {@code n} for the
     * national form. Checking the boundary there prevents an embedded
     * identifier sequence such as {@code identifiernq} from becoming a quote
     * opener while allowing the {@code n} in a valid {@code nq} prefix.
     * Paired opening delimiters use their matching closing character; every
     * other valid delimiter closes with itself.
     *
     * @param source SQL source
     * @param start candidate {@code q} or {@code nq} prefix offset, case-insensitive
     * @return closing delimiter, or NUL when the candidate is not a valid opener
     */
    public static char qQuoteClosingDelimiter(String source, int start) {
        // An incomplete candidate at end-of-input cannot contain q, the apostrophe, and an opening delimiter.
        if (start >= source.length()) {
            return '\0';
        }

        // Apply the boundary to the complete q/nq token. Testing at q would mistake the valid nq prefix's n for an
        // identifier continuation; ignoring the boundary would instead accept nq embedded in an identifier.
        if (start > 0 && identifierContinuation(source.charAt(start - 1))) {
            return '\0';
        }

        // Normalize both prefix forms to the q position because their apostrophe and delimiter grammar is identical.
        int qOffset = start;
        char prefix = source.charAt(start);
        if (prefix == 'n' || prefix == 'N') {
            qOffset++;
        }

        // A complete opener is q, an apostrophe, and one non-whitespace delimiter character.
        if (qOffset + 2 >= source.length()
                || (source.charAt(qOffset) != 'q' && source.charAt(qOffset) != 'Q')
                || source.charAt(qOffset + 1) != '\'') {
            return '\0';
        }
        char opening = source.charAt(qOffset + 2);
        if (opening == '\'' || Character.isWhitespace(opening) || Character.isISOControl(opening)) {
            return '\0';
        }

        // Oracle pairs the four bracket-like delimiters; any other valid delimiter closes with the same character.
        return switch (opening) {
        case '[' -> ']';
        case '(' -> ')';
        case '{' -> '}';
        case '<' -> '>';
        default -> opening;
        };
    }

    /**
     * Tests whether a character can continue the SQL token immediately before
     * a vendor-specific quote opener.
     *
     * @param character character before the candidate opener
     * @return whether the candidate is embedded in an existing token
     */
    private static boolean identifierContinuation(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '$';
    }
}
