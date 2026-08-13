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
package io.helidon.data.jdbc;

/**
 * Immutable lexical policy used to identify JDBC bind markers without parsing
 * database-specific SQL grammar.
 * <p>
 * The profile is deliberately package-private. JDBC currently exposes one
 * portable contract rather than a public dialect-selection API. Keeping each
 * rule explicit prevents later profiles from silently inheriting assumptions
 * which are valid for only one driver or database.
 */
enum JdbcLexicalProfile {

    /**
     * Portable marker policy used by every JDBC client.
     * <p>
     * Brackets and backticks are ordinary punctuation, doubled question marks
     * are two bind markers, and nested block comments are rejected. Valid
     * PostgreSQL dollar quotes and Oracle alternative quotes remain protected
     * because their complete opening delimiters are unambiguous.
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

    /**
     * Whether backticks delimit a protected identifier.
     *
     * @return whether backtick identifiers are protected
     */
    boolean backtickIdentifiers() {
        return backtickIdentifiers;
    }

    /**
     * Whether square brackets delimit a protected identifier.
     *
     * @return whether bracket identifiers are protected
     */
    boolean bracketIdentifiers() {
        return bracketIdentifiers;
    }

    /**
     * Whether doubled question marks represent one driver escape rather than
     * two bind markers.
     *
     * @return whether doubled question marks are an escape
     */
    boolean questionMarkEscape() {
        return questionMarkEscape;
    }

    /**
     * Whether block comments may nest.
     *
     * @return whether nested block comments are supported
     */
    boolean nestedBlockComments() {
        return nestedBlockComments;
    }

    /**
     * Whether valid PostgreSQL dollar quotes delimit protected strings.
     *
     * @return whether dollar-quoted strings are protected
     */
    boolean dollarQuotedStrings() {
        return dollarQuotedStrings;
    }

    /**
     * Whether valid {@code q} prefixed alternative quotes delimit protected strings.
     *
     * @return whether {@code q} quoted strings are protected
     */
    boolean qQuotedStrings() {
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
    boolean lineComment(String source, int start) {
        return JdbcSqlLexicalRules.lineComment(source, start);
    }
}
