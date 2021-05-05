/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.appl.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verify query operations of ORM (client side).
 */
public class QueryIT {
    
    @BeforeAll
    public static void setup() {
        ClientUtils.callTest("/test/QueryIT.setup");
    }

    @AfterAll
    public static void destroy() {
        ClientUtils.callTest("/test/QueryIT.destroy");
    }

    /**
     * Find trainer Ash and his pokemons.
     */
    @Test
    public void testDeleteEntity() {
        ClientUtils.callTest("/test/QueryIT.testFind");
    }

    /**
     * Query trainer Ash and his pokemons using JPQL.
     */
    @Test
    public void testDeleteJPQL() {
        ClientUtils.callTest("/test/QueryIT.testQueryJPQL");

    }

    /**
     * Query trainer Ash and his pokemons using CriteriaQuery.
     */
    @Test
    public void testDeleteCriteria() {
        ClientUtils.callTest("/test/QueryIT.testQueryCriteria");
    }

    /**
     * Query Celadon city using JPQL.
     */
    @Test
    public void testQueryCeladonJPQL() {
        ClientUtils.callTest("/test/QueryIT.testQueryCeladonJPQL");
    }

    /**
     * Query Celadon city using CriteriaQuery.
     */
    @Test
    public void testQueryCeladonCriteria() {
        ClientUtils.callTest("/test/QueryIT.testQueryCeladonCriteria");
    }

}
