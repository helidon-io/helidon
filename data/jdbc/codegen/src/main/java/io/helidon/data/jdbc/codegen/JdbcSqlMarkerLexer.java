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
package io.helidon.data.jdbc.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lexes declarative named and positional parameters without parsing SQL.
 * <p>
 * Marker-like text inside quoted regions and comments is copied unchanged.
 * Named markers are rewritten to JDBC {@code ?}. Positional markers already
 * have that form. Mixing the two styles is rejected. Other SQL text is copied
 * without validation so database syntax remains the driver's concern. The
 * private lexical profile makes every dialect-sensitive recognition
 * rule explicit and is verified against the runtime scanner's conformance
 * corpus.
 */
final class JdbcSqlMarkerLexer {

    private final String source;
    private final int length;
    private final JdbcLexicalProfile profile;
    private final StringBuilder jdbcSql;

    // Empty entries preserve the number and order of positional markers.
    private final List<String> markers = new ArrayList<>();
    private int index;
    private boolean named;
    private boolean positional;

    /**
     * Creates a lexer for one statement.
     *
     * @param source SQL source
     * @param profile marker lexical profile
     */
    private JdbcSqlMarkerLexer(String source, JdbcLexicalProfile profile) {
        this.source = source;
        this.length = source.length();
        this.profile = profile;
        this.jdbcSql = new StringBuilder(source.length());
    }

    /**
     * Parses one statement and returns its positional JDBC form.
     *
     * @param sql SQL statement
     * @return marker plan
     */
    static Result parse(String sql) {
        return parse(sql, JdbcLexicalProfile.PORTABLE);
    }

    /**
     * Parses one statement using an explicit lexical profile.
     *
     * @param sql SQL statement
     * @param profile marker lexical profile
     * @return marker plan
     */
    private static Result parse(String sql, JdbcLexicalProfile profile) {
        Objects.requireNonNull(sql, "The SQL statement must not be null.");
        Objects.requireNonNull(profile, "The JDBC lexical profile must not be null.");
        if (sql.isBlank()) {
            throw new IllegalArgumentException("The SQL statement must not be blank.");
        }
        JdbcSqlMarkerLexer lexer = new JdbcSqlMarkerLexer(sql, profile);
        lexer.scan();
        if (lexer.named && lexer.positional) {
            throw lexer.malformed("Declarative SQL cannot mix named and positional markers");
        }
        MarkerStyle style = lexer.named
                ? MarkerStyle.NAMED
                : lexer.positional ? MarkerStyle.POSITIONAL : MarkerStyle.NONE;
        return new Result(lexer.jdbcSql.toString(), List.copyOf(lexer.markers), style);
    }

    /**
     * Walks the SQL and dispatches protected regions or markers.
     */
    private void scan() {
        while (index < length) {
            char current = source.charAt(index);
            if (current == '\'') {
                // A single quote starts a string literal. Markers inside it are ordinary text.
                copyQuoted('\'');
            } else if (current == '"') {
                // A double quote starts a quoted identifier. Markers inside it are ordinary text.
                copyQuoted('"');
            } else if (current == '`' && profile.backtickIdentifiers()) {
                // Backtick protection is enabled only by a profile whose database defines it.
                copyQuoted('`');
            } else if (current == '[' && profile.bracketIdentifiers()) {
                // Bracket protection is enabled only by a profile whose database defines it.
                copyBracketIdentifier();
            } else if (current == '-' && peek(1) == '-' && profile.lineComment(source, index)) {
                // A line comment may contain marker-like text that must remain unchanged.
                copyLineComment();
            } else if (current == '/' && peek(1) == '*') {
                // A block comment may contain marker-like text that must remain unchanged.
                copyBlockComment();
            } else if ((current == 'q' || current == 'Q')
                    && profile.qQuotedStrings()
                    && JdbcSqlLexicalRules.qQuoteClosingDelimiter(source, index) != '\0') {
                // A q-quoted string protects everything up to its matching delimiter.
                copyQQuoted();
            } else if (profile.dollarQuotedStrings() && current == '$' && copyDollarQuoted()) {
                // The helper copied the complete quoted region and advanced past it.
                // Its contents must not be scanned for bind markers.
            } else if (current == ':') {
                // A colon may start a named marker or a literal SQL operator.
                namedMarker();
            } else if (current == '?') {
                // A question mark may be a positional marker or a database operator.
                positionalMarker();
            } else {
                // Preserve database syntax that has no bearing on JDBC marker recognition.
                jdbcSql.append(current);
                index++;
            }
        }
    }

    /**
     * Rewrites one named marker or copies a literal colon operator.
     */
    private void namedMarker() {
        char next = peek(1);
        if (next == ':' || next == '=') {
            jdbcSql.append(':').append(next);
            index += 2;
            return;
        }
        // Marker names use Java identifier rules because they resolve directly to repository parameters.
        if (!Character.isJavaIdentifierStart(next)) {
            jdbcSql.append(':');
            index++;
            return;
        }
        int start = index + 1;
        int end = start + 1;
        while (end < length && Character.isJavaIdentifierPart(source.charAt(end))) {
            end++;
        }
        if (end < length && source.charAt(end) == '.') {
            throw malformed("Dotted named parameters are not supported");
        }
        markers.add(source.substring(start, end));
        named = true;
        jdbcSql.append('?');
        index = end;
    }

    /**
     * Records one positional marker unless the selected profile defines a
     * doubled-question-mark driver escape.
     */
    private void positionalMarker() {
        char next = peek(1);
        if (next == '?' && profile.questionMarkEscape()) {
            jdbcSql.append('?').append(next);
            index += 2;
            return;
        }
        positional = true;
        markers.add("");
        jdbcSql.append('?');
        index++;
    }

    /**
     * Copies a single-, double-, or backtick-quoted region.
     *
     * @param delimiter quote character
     */
    private void copyQuoted(char delimiter) {
        jdbcSql.append(delimiter);
        index++;
        while (index < length) {
            char current = source.charAt(index++);
            jdbcSql.append(current);
            if (current == delimiter) {
                if (index < length && source.charAt(index) == delimiter) {
                    jdbcSql.append(delimiter);
                    index++;
                } else {
                    return;
                }
            }
        }
        throw malformed("Unterminated quoted SQL region");
    }

    /**
     * Copies a bracket-quoted identifier.
     */
    private void copyBracketIdentifier() {
        jdbcSql.append('[');
        index++;
        while (index < length) {
            char current = source.charAt(index++);
            jdbcSql.append(current);
            if (current == ']') {
                if (index < length && source.charAt(index) == ']') {
                    jdbcSql.append(']');
                    index++;
                } else {
                    return;
                }
            }
        }
        throw malformed("Unterminated bracket-quoted identifier");
    }

    /**
     * Copies a line comment through its line terminator.
     */
    private void copyLineComment() {
        jdbcSql.append("--");
        index += 2;
        while (index < length) {
            char current = source.charAt(index++);
            jdbcSql.append(current);
            if (current == '\n' || current == '\r') {
                return;
            }
        }
    }

    /**
     * Copies a block comment according to the selected nesting policy.
     */
    private void copyBlockComment() {
        jdbcSql.append("/*");
        index += 2;
        int depth = 1;
        while (index < length) {
            if (source.charAt(index) == '/' && peek(1) == '*') {
                if (!profile.nestedBlockComments()) {
                    throw malformed("Nested block comment is not supported");
                }
                jdbcSql.append("/*");
                index += 2;
                depth++;
            } else if (source.charAt(index) == '*' && peek(1) == '/') {
                jdbcSql.append("*/");
                index += 2;
                if (--depth == 0) {
                    return;
                }
            } else {
                jdbcSql.append(source.charAt(index++));
            }
        }
        throw malformed("Unterminated block comment");
    }

    /**
     * Copies a {@code q} quoted string.
     */
    private void copyQQuoted() {
        char closing = JdbcSqlLexicalRules.qQuoteClosingDelimiter(source, index);
        jdbcSql.append(source, index, index + 3);
        index += 3;
        while (index + 1 < length) {
            char current = source.charAt(index++);
            jdbcSql.append(current);
            if (current == closing && source.charAt(index) == '\'') {
                jdbcSql.append('\'');
                index++;
                return;
            }
        }
        throw malformed("Unterminated alternative quoted string");
    }

    /**
     * Copies a PostgreSQL dollar-quoted string when the current dollar sign
     * begins a valid delimiter.
     *
     * @return whether a dollar-quoted region was copied
     */
    private boolean copyDollarQuoted() {
        String delimiter = JdbcSqlLexicalRules.dollarDelimiter(source, index);
        if (delimiter == null) {
            return false;
        }
        int contentEnd = source.indexOf(delimiter, index + delimiter.length());
        if (contentEnd < 0) {
            throw malformed("Unterminated dollar-quoted string");
        }
        int end = contentEnd + delimiter.length();
        jdbcSql.append(source, index, end);
        index = end;
        return true;
    }

    /**
     * Returns a character relative to the current offset.
     *
     * @param offset relative offset
     * @return source character or the null character past the end
     */
    private char peek(int offset) {
        int target = index + offset;
        return target < length ? source.charAt(target) : '\0';
    }

    /**
     * Creates a diagnostic with the current source offset.
     *
     * @param message diagnostic text
     * @return malformed SQL exception
     */
    private IllegalArgumentException malformed(String message) {
        return new IllegalArgumentException(message + ". The lexical profile is " + profile
                                                    + ", and the SQL offset is " + index + ".");
    }

    /**
     * Marker syntax used by a statement.
     */
    enum MarkerStyle {

        NONE,
        NAMED,
        POSITIONAL
    }

    /**
     * Rewritten SQL and ordered marker information.
     *
     * @param sql positional JDBC SQL
     * @param markers named markers or empty positional entries
     * @param style marker style
     */
    record Result(String sql, List<String> markers, MarkerStyle style) {
    }

    /**
     * Immutable lexical policy used to identify and rewrite JDBC bind markers
     * without parsing database-specific SQL grammar.
     */
    private enum JdbcLexicalProfile {

        /**
         * Portable marker policy used for every generated JDBC repository.
         * <p>
         * Brackets and backticks are ordinary punctuation, doubled question
         * marks are two bind markers, and nested block comments are rejected.
         * Valid PostgreSQL dollar quotes and Oracle alternative quotes remain
         * protected because their complete opening delimiters are unambiguous.
         */
        PORTABLE(false, false, false, false, true, true);

        private final boolean backtickIdentifiers;
        private final boolean bracketIdentifiers;
        private final boolean questionMarkEscape;
        private final boolean nestedBlockComments;
        private final boolean dollarQuotedStrings;
        private final boolean qQuotedStrings;

        JdbcLexicalProfile(boolean backtickIdentifiers,
                           boolean bracketIdentifiers,
                           boolean questionMarkEscape,
                           boolean nestedBlockComments,
                           boolean dollarQuotedStrings,
                           boolean qQuotedStrings) {
            this.backtickIdentifiers = backtickIdentifiers;
            this.bracketIdentifiers = bracketIdentifiers;
            this.questionMarkEscape = questionMarkEscape;
            this.nestedBlockComments = nestedBlockComments;
            this.dollarQuotedStrings = dollarQuotedStrings;
            this.qQuotedStrings = qQuotedStrings;
        }

        private boolean backtickIdentifiers() {
            return backtickIdentifiers;
        }

        private boolean bracketIdentifiers() {
            return bracketIdentifiers;
        }

        private boolean questionMarkEscape() {
            return questionMarkEscape;
        }

        private boolean nestedBlockComments() {
            return nestedBlockComments;
        }

        private boolean dollarQuotedStrings() {
            return dollarQuotedStrings;
        }

        private boolean qQuotedStrings() {
            return qQuotedStrings;
        }

        /**
         * Tests the portable line-comment delimiter. Requiring conventional
         * whitespace avoids hiding a MySQL bind in an expression such as
         * {@code balance--?}.
         *
         * @param source SQL source
         * @param start first dash offset
         * @return whether the two dashes begin a protected line comment
         */
        private boolean lineComment(String source, int start) {
            // The offset identifies the first dash, so advance past both dashes before examining the next character.
            int contentStart = start + 2;
            if (contentStart == source.length()) {
                return true;
            }
            char next = source.charAt(contentStart);
            return Character.isWhitespace(next) || Character.isISOControl(next);
        }
    }

    /**
     * Lexical delimiter rules used only by this scanner.
     */
    private static final class JdbcSqlLexicalRules {

        private JdbcSqlLexicalRules() {
        }

        /**
         * Recognizes a PostgreSQL dollar-quote delimiter at a token boundary.
         *
         * @param source SQL source
         * @param start candidate opening dollar index
         * @return complete delimiter, or {@code null}
         */
        private static String dollarDelimiter(String source, int start) {
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
         * Recognizes a {@code q} quoted string opening delimiter at a token boundary.
         *
         * @param source SQL source
         * @param start candidate {@code q} or {@code Q} offset
         * @return closing delimiter, or NUL when the candidate is not a valid opener
         */
        private static char qQuoteClosingDelimiter(String source, int start) {
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

}
