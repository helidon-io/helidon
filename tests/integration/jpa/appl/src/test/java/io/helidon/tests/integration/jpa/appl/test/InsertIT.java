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
import org.junit.jupiter.api.Test;

/**
 * Verify update operations of ORM (clienbt side).
 */
public class InsertIT {
    
    @AfterAll
    public static void destroy() {
        ClientUtils.callTest("/test/InsertIT.destroy");
    }

    /**
     * Verify simple create operation (persist) on a single database row.
     */
    @Test
    public void testInsertType() {
        ClientUtils.callTest("/test/InsertIT.testInsertType");
    }

    /**
     * Verify complex create operation (persist) on a full ORM model (Gary Oak and his 6 pokemons).
     * Relations are not marked for cascade persist operation so every entity instance has to be persisted separately.
     */
    @Test
    public void testInsertTrainerWithPokemons() {
        ClientUtils.callTest("/test/InsertIT.testInsertTrainerWithPokemons");
    }

    /**
     * Verify complex create operation (persist) on a full ORM model (Lt. Surge in Vermilion City).
     */
    @Test
    public void testInsertTownWithStadium() {
        ClientUtils.callTest("/test/InsertIT.testInsertTownWithStadium");
    }

}
