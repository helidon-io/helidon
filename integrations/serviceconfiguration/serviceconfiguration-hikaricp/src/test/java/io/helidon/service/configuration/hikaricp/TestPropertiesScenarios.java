/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.service.configuration.hikaricp;

import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Deprecated
public class TestPropertiesScenarios {

    public TestPropertiesScenarios() {
        super();
    }

    @Test
    public void testLocalhostIsPresent() {
        final Set<? extends io.helidon.service.configuration.api.System> systems =
            io.helidon.service.configuration.api.System.getSystems();
        assertNotNull(systems);
        assertEquals(1, systems.size());
        final io.helidon.service.configuration.api.System system = systems.iterator().next();
        assertNotNull(system);
        assertEquals("localhost", system.getName());
    }

}
