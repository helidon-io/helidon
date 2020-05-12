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
 * Verify delete operations of ORM (client side).
 */
public class DeleteIT {
    
    @BeforeAll
    public static void setup() {
        ClientUtils.callTest("/test/DeleteIT.setup");
    }

    @AfterAll
    public static void destroy() {
        ClientUtils.callTest("/test/DeleteIT.destroy");
    }

    /**
     * Delete pokemon: release Misty's Staryu.
     * Modification is done using entity instance.
     */
    @Test
    public void testDeleteEntity() {
        ClientUtils.callTest("/test/DeleteIT.testDeleteEntity");
    }

    /**
     * Delete pokemon: release Misty's Psyduck.
     * Modification is done using JPQL.
     */
    @Test
    public void testDeleteJPQL() {
        ClientUtils.callTest("/test/DeleteIT.testDeleteJPQL");

    }

    /**
     * Delete pokemon: release Misty's Corsola.
     * Modification is done using CriteriaUpdate.
     */
    @Test
    public void testDeleteCriteria() {
        ClientUtils.callTest("/test/DeleteIT.testDeleteCriteria");
    }

}
