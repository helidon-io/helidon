/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@link StatementParsers}.
 */
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
        assertEquals(stmtExp, stmtOut);
    }

}
