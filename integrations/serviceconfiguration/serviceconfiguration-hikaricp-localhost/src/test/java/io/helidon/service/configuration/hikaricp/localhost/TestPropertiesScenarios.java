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
package io.helidon.service.configuration.hikaricp.localhost;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Deprecated
public class TestPropertiesScenarios {

    public TestPropertiesScenarios() {
        super();
    }

    @Test
    public void testBareBones() {
        final io.helidon.service.configuration.api.ServiceConfiguration sc =
            io.helidon.service.configuration.api.ServiceConfiguration.getInstance("hikaricp");
        assertNotNull(sc);
        assertEquals("jdbc:h2:mem:test", sc.getProperty("javax.sql.DataSource.dataSource.url"));
    }

    @Test
    public void testJustInTimePropertyCreation() {
        final io.helidon.service.configuration.api.ServiceConfiguration sc =
            io.helidon.service.configuration.api.ServiceConfiguration.getInstance("hikaricp");
        assertNotNull(sc);
        assertEquals("jdbc:h2:mem:fred", sc.getProperty("javax.sql.DataSource.fred.dataSource.url"));
    }
  
}
