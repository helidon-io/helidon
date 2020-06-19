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
package io.helidon.tests.integration.jpa.simple.init;

import io.helidon.tests.integration.jpa.simple.DbUtils;
import io.helidon.tests.integration.jpa.simple.PU;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Initialize tests.
 */
public class InitIT {
    
    private static PU pu;

    @BeforeAll
    public static void setup() {
        pu = PU.getInstance();
    }

    @AfterAll
    public static void destroy() {
        pu = null;
    }


    @Test
    public void testInsertData() {
        pu.tx(pu -> DbUtils.dbInit(pu));
    }

}
