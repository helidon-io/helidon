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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcSqlMarkerLexerTest {

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

    @Test
    void preservesDriverEscapesAndCountsPositionalMarkers() {
        String sql = "select DOCUMENT ?? 'name', TAGS ??| ARRAY['a'], TAGS ??& ARRAY['a', 'b'], "
                + "PAYLOAD @?? '$.items[*]' from T where ID = ?";

        JdbcSqlMarkerLexer.Result result = JdbcSqlMarkerLexer.parse(sql);

        assertThat(result.sql(), is(sql));
        assertThat(result.markers(), is(List.of("")));
        assertThat(result.style(), is(JdbcSqlMarkerLexer.MarkerStyle.POSITIONAL));
    }

    @Test
    void preservesDriverEscapesWithNamedMarkers() {
        String sql = "select PAYLOAD @?? '$.items[*]' from T where ID = :id";

        JdbcSqlMarkerLexer.Result result = JdbcSqlMarkerLexer.parse(sql);

        assertThat(result.sql(), is("select PAYLOAD @?? '$.items[*]' from T where ID = ?"));
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
                select ':literal', "quoted:name", `mysql:name`, [sql:name], value::text, data ??| array['x']
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
    void rejectsMixedMarkersAndMalformedRegions() {
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select :id, ?"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select :user.id"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select 'unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select \"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select `unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select [unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select /* unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select $tag$unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSqlMarkerLexer.parse("select q'[unterminated"));
    }
}
