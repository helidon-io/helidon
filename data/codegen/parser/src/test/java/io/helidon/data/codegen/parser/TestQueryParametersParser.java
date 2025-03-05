/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.codegen.parser;

import io.helidon.data.codegen.query.QueryParameters;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestQueryParametersParser {

    @Test
    void testNamedParameters() {
        String jdql = "SELECT * FROM Entity e WHERE e.name = :name AND e.length = :length";
        QueryParametersParser parser = QueryParametersParser.create();
        QueryParameters parameters = parser.parse(jdql);
        assertThat(parameters.isEmpty(), is(false));
    }

    @Test
    void testOrdinalParameters() {
        String jdql = "SELECT * FROM Entity e WHERE e.name = $1 AND e.length = $2";
        QueryParametersParser parser = QueryParametersParser.create();
        QueryParameters parameters = parser.parse(jdql);
        assertThat(parameters.isEmpty(), is(false));
    }

}
