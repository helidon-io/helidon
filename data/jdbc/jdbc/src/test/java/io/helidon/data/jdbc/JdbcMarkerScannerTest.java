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

class JdbcMarkerScannerTest {

    @Test
    void appliesPortablePunctuationAndCommentRules() {
        String sql = """
                select [question?], `question?`, ?, ??
                from T
                where BALANCE--? > 0 and ID = ?
                -- ? ignored
                /* ? ignored */
                """;

        assertThat(JdbcOperation.parameterCount(sql), is(7));
        assertThat(JdbcOperation.parameterCount("select 1 -- ? ignored"), is(0));
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
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select :id"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select 'unterminated"));
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
        assertThat(failure.getMessage(), endsWith("offset 16"));
        assertThat(failure.getMessage(), not(containsString(sql)));
    }
}
