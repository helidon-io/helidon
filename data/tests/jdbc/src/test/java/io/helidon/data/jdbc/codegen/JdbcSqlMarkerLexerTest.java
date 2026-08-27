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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcSqlMarkerLexerTest {

    /**
     * Verifies that named and positional marker plans preserve portable
     * punctuation, recognized comments, and separated subtraction operators.
     */
    @Test
    void appliesPortablePunctuationAndCommentRules() {
        String sql = """
                select VALUES_COLUMN[:index]
                from T
                where BALANCE - -:delta > 0 and ID = :id
                -- :ignored
                """;

        JdbcSqlMarkerLexer.Result named = JdbcSqlMarkerLexer.parse(sql);
        JdbcSqlMarkerLexer.Result positional = JdbcSqlMarkerLexer.parse("select [question?], `question?`, ?");

        assertThat(named.sql(), is(sql.replace(":index", "?")
                                            .replace(":delta", "?")
                                            .replace(":id", "?")));
        assertThat(named.markers(), is(List.of("index", "delta", "id")));
        assertThat(named.style(), is(JdbcSqlMarkerLexer.MarkerStyle.NAMED));
        assertThat(positional.markers(), is(List.of("", "")));
    }

    /**
     * Verifies that declarative SQL rejects ambiguous double-dash input before
     * named-marker rewriting can inspect database-comment text.
     */
    @Test
    void rejectsAmbiguousDoubleDashSequences() {
        String sql = "select PRIVATE_VALUE--:id";

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                         () -> JdbcSqlMarkerLexer.parse(sql));

        assertThat(failure.getMessage(), containsString("Ambiguous double-dash SQL sequence"));
        assertThat(failure.getMessage(), containsString("PORTABLE"));
        assertThat(failure.getMessage(), endsWith("offset is 20."));
        assertThat(failure.getMessage(), not(containsString("PRIVATE_VALUE")));
    }

    @Test
    void rewritesNamedMarkersInEncounterOrder() {
        JdbcSqlMarkerLexer.Result result = JdbcSqlMarkerLexer.parse(
                "select * from T where ID = :id or PARENT_ID = :id and NAME = :name");

        assertThat(result.sql(), is("select * from T where ID = ? or PARENT_ID = ? and NAME = ?"));
        assertThat(result.markers(), is(List.of("id", "id", "name")));
        assertThat(result.style(), is(JdbcSqlMarkerLexer.MarkerStyle.NAMED));
    }

    @Test
    void acceptsOneOrSeveralPositionalMarkers() {
        JdbcSqlMarkerLexer.Result one = JdbcSqlMarkerLexer.parse("select NAME from T where ID = ?");
        JdbcSqlMarkerLexer.Result several = JdbcSqlMarkerLexer.parse("update T set NAME = ? where ID = ?");

        assertThat(one.markers(), is(List.of("")));
        assertThat(one.style(), is(JdbcSqlMarkerLexer.MarkerStyle.POSITIONAL));
        assertThat(several.markers(), is(List.of("", "")));
    }

    /**
     * Verifies that a doubled question mark is preserved without contributing
     * physical bind positions to a generated statement.
     */
    @Test
    void protectsDoubledQuestionMarkEscape() {
        String sql = "select ??";

        JdbcSqlMarkerLexer.Result result = JdbcSqlMarkerLexer.parse(sql);

        assertThat(result.sql(), is(sql));
        assertThat(result.markers(), is(List.of()));
        assertThat(result.style(), is(JdbcSqlMarkerLexer.MarkerStyle.NONE));
    }

    /**
     * Verifies that a doubled question mark can coexist with declarative named
     * markers without being mistaken for positional syntax.
     */
    @Test
    void acceptsDoubledQuestionMarkEscapeWithNamedMarkers() {
        String sql = "select PAYLOAD @?? '$.items[*]' from T where ID = :id";

        JdbcSqlMarkerLexer.Result result = JdbcSqlMarkerLexer.parse(sql);

        assertThat(result.sql(), is("select PAYLOAD @?? '$.items[*]' from T where ID = ?"));
        assertThat(result.markers(), is(List.of("id")));
        assertThat(result.style(), is(JdbcSqlMarkerLexer.MarkerStyle.NAMED));
    }

    /**
     * Verifies that named-marker rewriting ignores PostgreSQL escape strings
     * and MySQL backtick identifiers while preserving their exact source.
     */
    @Test
    void protectsExtendedPortableRegions() {
        String sql = "select E'value \\' :ignored', `identifier:ignored` from T where ID = :id";

        JdbcSqlMarkerLexer.Result result = JdbcSqlMarkerLexer.parse(sql);

        assertThat(result.sql(),
                   is("select E'value \\' :ignored', `identifier:ignored` from T where ID = ?"));
        assertThat(result.markers(), is(List.of("id")));
        assertThat(result.style(), is(JdbcSqlMarkerLexer.MarkerStyle.NAMED));
    }

    @Test
    void treatsEveryUnescapedQuestionMarkAsMarker() {
        String sql = "select DOCUMENT ? 'name', TAGS ?| ARRAY['a'], TAGS ?& ARRAY['a'], "
                + "PAYLOAD @? '$.items[*]'";

        JdbcSqlMarkerLexer.Result result = JdbcSqlMarkerLexer.parse(sql);

        assertThat(result.markers(), is(List.of("", "", "", "")));
        assertThat(result.style(), is(JdbcSqlMarkerLexer.MarkerStyle.POSITIONAL));
    }

    @Test
    void distinguishesNamedMarkersFromCastsAndAssignmentOperators() {
        String sql = "select :value::jsonb from T where VERSION := VERSION and ID = :id";

        JdbcSqlMarkerLexer.Result result = JdbcSqlMarkerLexer.parse(sql);

        assertThat(result.sql(), is("select ?::jsonb from T where VERSION := VERSION and ID = ?"));
        assertThat(result.markers(), is(List.of("value", "id")));
    }

    @Test
    void protectsQuotedCommentedAndVendorSyntax() {
        String sql = """
                select ':literal', "quoted:name", value::text
                from T -- :line and ?
                where ID = :id /* :block and ? */
                  and BODY = $tag$:dollar and ?$tag$ and Q = q'[oracle:name and ?]'
                """;

        JdbcSqlMarkerLexer.Result result = JdbcSqlMarkerLexer.parse(sql);

        assertThat(result.markers(), is(List.of("id")));
        assertThat(result.sql(), is(sql.replace("ID = :id", "ID = ?")));
    }

    @Test
    void preservesSqlOutsideMarkerSyntax() {
        String sql = "select <name> from #contacts where ID = :id";

        JdbcSqlMarkerLexer.Result result = JdbcSqlMarkerLexer.parse(sql);

        assertThat(result.sql(), is("select <name> from #contacts where ID = ?"));
        assertThat(result.markers(), is(List.of("id")));
        assertThat(result.style(), is(JdbcSqlMarkerLexer.MarkerStyle.NAMED));
    }

    @Test
    void appliesDollarQuoteGrammarOnlyAtTokenBoundaries() {
        String sql = "select $$:empty and ?$$, ($tag$:named and ?$tag$), identifier$tag$ "
                + "from T where ID = :id";

        JdbcSqlMarkerLexer.Result result = JdbcSqlMarkerLexer.parse(sql);

        assertThat(result.sql(), is(sql.replace(":id", "?")));
        assertThat(result.markers(), is(List.of("id")));
        assertThat(JdbcSqlMarkerLexer.parse("select $1$ ?").markers(), is(List.of("")));
        assertThat(JdbcSqlMarkerLexer.parse("select $bad-tag$ ?").markers(), is(List.of("")));
    }

    @Test
    void rejectsUnterminatedDollarQuotesOnlyForValidOpeners() {
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select $$unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select ($tag$unterminated"));

        JdbcSqlMarkerLexer.Result identifier = JdbcSqlMarkerLexer.parse("select identifier$tag$ from T where ID = :id");
        assertThat(identifier.markers(), is(List.of("id")));
    }

    @Test
    void rejectsMixedMarkersAndMalformedRegions() {
        IllegalArgumentException mixed = assertThrows(IllegalArgumentException.class,
                                                      () -> JdbcSqlMarkerLexer.parse("select :id, ?"));
        assertThat(mixed.getMessage(),
                   is("Declarative SQL cannot mix named and positional markers. The lexical profile is PORTABLE, "
                              + "and the SQL offset is 13."));
        IllegalArgumentException dotted = assertThrows(IllegalArgumentException.class,
                                                       () -> JdbcSqlMarkerLexer.parse("select :user.id"));
        assertThat(dotted.getMessage(),
                   is("Dotted named parameters are not supported. The lexical profile is PORTABLE, and the SQL "
                              + "offset is 7."));
        IllegalArgumentException quoted = assertThrows(IllegalArgumentException.class,
                                                       () -> JdbcSqlMarkerLexer.parse("select 'unterminated"));
        assertThat(quoted.getMessage(),
                   is("Unterminated quoted SQL region. The lexical profile is PORTABLE, and the SQL offset is 20."));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select \"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select /* unterminated"));
        assertThrows(IllegalArgumentException.class,
                     () -> JdbcSqlMarkerLexer.parse("select /* outer /* nested */ outer */"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select $tag$unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select q'[unterminated"));
    }

    @Test
    void reportsPortableProfileFailuresWithoutRenderingSql() {
        String sql = "select /* outer /* nested */ outer */";

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                         () -> JdbcSqlMarkerLexer.parse(sql));

        assertThat(failure.getMessage(), containsString("PORTABLE"));
        assertThat(failure.getMessage(), endsWith("offset is 16."));
        assertThat(failure.getMessage(), not(containsString(sql)));
    }
}
