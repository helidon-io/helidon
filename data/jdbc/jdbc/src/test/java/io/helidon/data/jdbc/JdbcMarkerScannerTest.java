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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcMarkerScannerTest {

    @Test
    void countsTheSameProtectedRegionsAndDriverEscapesAsDeclarativeCodegen() {
        String sql = """
                select ':literal', "quoted:name", `mysql:name`, [sql:name],
                       value::text, JSON_VALUE ?? 'name',
                       TAGS ??| ARRAY['a'], TAGS ??& ARRAY['a', 'b'],
                       PAYLOAD @?? '$.items[*]'
                from T -- :line and ?
                where ID = ? /* :block and ? */
                  and BODY = $tag$:dollar and ?$tag$
                  and Q = q'[oracle:name and ?]'
                """;

        assertThat(JdbcOperation.parameterCount(sql), is(1));
    }

    @Test
    void treatsEveryUnescapedQuestionMarkAsMarker() {
        String sql = """
                select DOCUMENT ? 'name',
                       TAGS ?| ARRAY['a'],
                       TAGS ?& ARRAY['a', 'b'],
                       PAYLOAD @? '$.items[*]'
                """;

        assertThat(JdbcOperation.parameterCount(sql), is(4));
    }

    @Test
    void rejectsRuntimeNamedMarkersAndEveryMalformedProtectedRegion() {
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select :id"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select 'unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select \"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select `unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select [unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select /* unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select $tag$unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JdbcOperation.parameterCount("select q'[unterminated"));
    }
}
