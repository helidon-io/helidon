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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcParameterCountTest {

    /**
     * Verifies that portable punctuation stays visible to marker recognition
     * while comments and separated subtraction operators remain unambiguous.
     */
    @Test
    void appliesPortablePunctuationAndCommentRules() {
        String sql = """
                select [question?], `question?`, ?, ??
                from T
                where BALANCE - -? > 0 and ID = ?
                -- ? ignored
                /* ? ignored */
                """;

        assertThat(JdbcOperation.parameterCount(sql), is(4));
        assertThat(JdbcOperation.parameterCount("select 1 -- ? ignored"), is(0));
    }

    /**
     * Verifies that imperative marker counting protects the extended portable
     * regions while retaining ordinary single-quote behavior.
     */
    @Test
    void protectsExtendedPortableRegions() {
        assertThat(JdbcOperation.parameterCount("select PAYLOAD @?? ? from T"), is(1));
        assertThat(JdbcOperation.parameterCount("select `identifier?` from T where ID = ?"), is(1));
        assertThat(JdbcOperation.parameterCount("select E'value \\' ? ignored', ?"), is(1));
        assertThat(JdbcOperation.parameterCount("select 'ends with \\', ?"), is(1));
    }

    /**
     * Verifies that imperative statement creation rejects a no-whitespace
     * double dash through the shared safe lexical diagnostic.
     */
    @Test
    void rejectsAmbiguousDoubleDashSequences() {
        String sql = "select PRIVATE_VALUE--comment";

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                         () -> JdbcOperation.parameterCount(sql));

        assertThat(failure.getMessage(), containsString("Ambiguous double-dash SQL sequence"));
        assertThat(failure.getMessage(), containsString("PORTABLE"));
        assertThat(failure.getMessage(), endsWith("offset is 20."));
        assertThat(failure.getMessage(), not(containsString("PRIVATE_VALUE")));
    }

    @Test
    void appliesDollarQuoteGrammarOnlyAtTokenBoundaries() {
        String sql = "select $$?$$, ($tag$?$tag$), identifier$tag$ from T where ID = ?";

        assertThat(JdbcOperation.parameterCount(sql), is(1));
        assertThat(JdbcOperation.parameterCount("select $1$ ?"), is(1));
        assertThat(JdbcOperation.parameterCount("select $bad-tag$ ?"), is(1));
    }

    @Test
    void rejectsUnterminatedDollarQuotesOnlyForValidOpeners() {
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select $$unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select ($tag$unterminated"));

        assertThat(JdbcOperation.parameterCount("select identifier$tag$ from T where ID = ?"), is(1));
    }

    @Test
    void rejectsRuntimeNamedMarkersAndEveryMalformedProtectedRegion() {
        IllegalArgumentException named = assertThrows(IllegalArgumentException.class,
                                                      () -> JdbcOperation.parameterCount("select :id"));
        assertThat(named.getMessage(),
                   is("JdbcClient SQL accepts only positional '?' markers. A named marker was found for lexical "
                              + "profile PORTABLE at offset 7."));
        IllegalArgumentException quoted = assertThrows(IllegalArgumentException.class,
                                                       () -> JdbcOperation.parameterCount("select 'unterminated"));
        assertThat(quoted.getMessage(),
                   is("Unterminated quoted SQL region. The lexical profile is PORTABLE, and the SQL offset is 20."));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select \"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select /* unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select $tag$unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select q'[unterminated"));
    }

    @Test
    void reportsPortableProfileFailuresWithoutRenderingSql() {
        String sql = "select /* outer /* nested */ outer */";

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                         () -> JdbcOperation.parameterCount(sql));

        assertThat(failure.getMessage(), containsString("PORTABLE"));
        assertThat(failure.getMessage(), endsWith("offset is 16."));
        assertThat(failure.getMessage(), not(containsString(sql)));
    }
}
