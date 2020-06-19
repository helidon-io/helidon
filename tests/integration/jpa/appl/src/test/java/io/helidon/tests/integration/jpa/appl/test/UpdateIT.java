/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
 * Verify update operations of ORM (client side).
 */
public class UpdateIT {

    @BeforeAll
    public static void setup() {
        ClientUtils.callTest("/test/UpdateIT.setup");
    }

    @AfterAll
    public static void destroy() {
        ClientUtils.callTest("/test/UpdateIT.destroy");
    }

    /**
     * Update pokemon: evolve Broke's Geodude into Graveler.
     * Modification is done using entity instance.
     */
    @Test
    public void testUpdateEntity() {
        ClientUtils.callTest("/test/UpdateIT.testUpdateEntity");
    }

    /**
     * Update pokemon: evolve Broke's Slowpoke into Slowbro.
     * Modification is done using JPQL.
     */
    @Test
    public void testUpdateJPQL() {
        ClientUtils.callTest("/test/UpdateIT.testUpdateJPQL");
    }

    /**
     * Update pokemon: evolve Broke's Teddiursa into Ursaring.
     * Modification is done using CriteriaUpdate.
     */
    @Test
    public void testUpdateCriteria() {
        ClientUtils.callTest("/test/UpdateIT.testUpdateCriteria");
    }

}
