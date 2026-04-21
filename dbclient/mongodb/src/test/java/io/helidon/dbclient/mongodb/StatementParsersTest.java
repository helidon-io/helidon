/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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
package io.helidon.dbclient.mongodb;

import java.util.HashMap;
import java.util.Map;

import io.helidon.dbclient.mongodb.StatementParsers.NamedParser;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link StatementParsers}.
 */
@SuppressWarnings("SpellCheckingInspection")
public class StatementParsersTest {

    /**
     * Test simple MongoDb statement with parameters and mapping.
     */
    @Test
    void testStatementWithParameters() {
        String stmtIn = "{ id: { $gt: $idmin }, id: { $lt: $idmax } }";
        Map<String, Integer> mapping = new HashMap<>(2);
        mapping.put("idmin", 1);
        mapping.put("idmax", 7);
        String stmtExp = stmtIn
                .replace("$idmin", String.valueOf(mapping.get("idmin")))
                .replace("$idmax", String.valueOf(mapping.get("idmax")));
        NamedParser parser = new NamedParser(stmtIn, mapping);
        String stmtOut = parser.convert();
        assertThat(stmtOut, is(stmtExp));
    }

    @Test
    void testStatementWithHelidonJsonValues() {
        JsonObject jsonObject = JsonObject.builder()
                .set("name", "Pikachu")
                .set("id", 25)
                .set("active", true)
                .set("moves", JsonArray.createStrings(java.util.List.of("thunderbolt", "quick attack")))
                .build();

        assertThat(StatementParsers.toJson(JsonString.create("electric")), is("\"electric\""));
        assertThat(StatementParsers.toJson(jsonObject),
                   is("{\"name\":\"Pikachu\",\"id\":25,\"active\":true,\"moves\":[\"thunderbolt\",\"quick attack\"]}"));
    }

    @Test
    void testStatementWithSpecialFloatingPointValues() {
        assertThat(StatementParsers.toJson(Double.NaN), is("\"NaN\""));
        assertThat(StatementParsers.toJson(Float.POSITIVE_INFINITY), is("\"Infinity\""));
        assertThat(StatementParsers.toJson(Double.NEGATIVE_INFINITY), is("\"-Infinity\""));
        assertThat(StatementParsers.toJson(42.5d), is("42.5"));
    }

}
