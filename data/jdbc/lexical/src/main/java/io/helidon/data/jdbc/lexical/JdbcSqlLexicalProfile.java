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
 * Immutable rules used to recognize JDBC bind markers without parsing SQL
 * grammar.
 */
@Api.Internal
public enum JdbcSqlLexicalProfile {

    /**
     * Portable marker rules used by generated repositories and imperative
     * JDBC clients.
     * <p>
     * Square brackets and backticks remain ordinary punctuation. Each
     * question mark is a bind marker. A no-whitespace double-dash sequence and
     * nested block comments are rejected. Complete PostgreSQL dollar quoted
     * strings and Oracle alternative quoted strings are protected regions.
     */
    PORTABLE(false, false, false, false, true, true);

    private final boolean backtickIdentifiers;
    private final boolean bracketIdentifiers;
    private final boolean questionMarkEscape;
    private final boolean nestedBlockComments;
    private final boolean dollarQuotedStrings;
    private final boolean qQuotedStrings;

    JdbcSqlLexicalProfile(boolean backtickIdentifiers,
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

    boolean backtickIdentifiers() {
        return backtickIdentifiers;
    }

    boolean bracketIdentifiers() {
        return bracketIdentifiers;
    }

    boolean questionMarkEscape() {
        return questionMarkEscape;
    }

    boolean nestedBlockComments() {
        return nestedBlockComments;
    }

    boolean dollarQuotedStrings() {
        return dollarQuotedStrings;
    }

    boolean qQuotedStrings() {
        return qQuotedStrings;
    }
}
