/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.service.configuration.ucp.accs;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
    public void testBareBones() {
        final io.helidon.service.configuration.api.ServiceConfiguration sc =
            io.helidon.service.configuration.api.ServiceConfiguration.getInstance("ucp");
        assertNull(sc);
    }

    @Test
    public void testACCSSystem() {
        final Map<String, String> env = new HashMap<>();
        env.put("MYSQLCS_CONNECT_STRING", "TODO");
        env.put("MYSQLCS_USER_NAME", "sa");
        env.put("MYSQLCS_USER_PASSWORD", "sa");
        final io.helidon.service.configuration.api.System dummyAccsSystem =
            new io.helidon.service.configuration.api.System("accs", true) {
                @Override
                public final boolean isEnabled() {
                    return true;
                }

                @Override
                public final Map<String, String> getenv() {
                    return env;
                }
            };
        final UCPServiceConfigurationACCSProvider provider = new UCPServiceConfigurationACCSProvider();
        final io.helidon.service.configuration.api.ServiceConfiguration sc =
            provider.buildFor(Collections.singleton(dummyAccsSystem), null);
        assertNotNull(sc);
        assertEquals("jdbc:mysql://TODO", sc.getProperty("javax.sql.DataSource.dataSource.url"));
    
    }
  
}
