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

package io.helidon.microprofile.config;

import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.CollectionsHelper.mapOf;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link MpcSourceEnvironmentVariables}.
 */
public class MpcSourceEnvironmentVariablesTest {
    private static ConfigSource APP_SOURCE;

    @BeforeAll
    static void initClass() {
        final Map<String, String> appValues = mapOf("app.key", "app-value",
                                                    "com.acme.size", "app-value",
                                                    "server.executor-service.max-pool-size", "app-value");

        final ConfigSource appSource = new ConfigSource() {
            @Override
            public Map<String, String> getProperties() {
                return appValues;
            }

            @Override
            public String getValue(String propertyName) {
                return appValues.get(propertyName);
            }

            @Override
            public String getName() {
                return "helidon:unit-test";
            }
        };
    }

    @Test
    public void testEnvironmentVariablesSourceMappings() {
        MpConfig config = (MpConfig) MpConfig.builder()
                                             .withSources(new MpcSourceEnvironmentVariables())
                                             .build();

        assertValue("simple", "unmapped-env-value", config);

        assertValue("_unmapped", "unmapped-env-value", config);
        assertThat(config.getOptionalValue(".unmapped", String.class).isPresent(), Matchers.is(false));

        assertValue("com_ACME_size", "mapped-env-value", config);
        assertValue("com.ACME.size", "mapped-env-value", config);
        assertValue("com.acme.size", "mapped-env-value", config);

        assertValue("SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE", "mapped-env-value", config);
        assertValue("SERVER.EXECUTOR-SERVICE.MAX-POOL-SIZE", "mapped-env-value", config);
        assertValue("server.executor-service.max-pool-size", "mapped-env-value", config);
    }

    @Test
    public void testEnvironmentVariableOverrides() {
        final ConfigSource appSource = toConfigSource(mapOf("app.key", "app-value",
                                                            "com.acme.size", "app-value",
                                                            "server.executor-service.max-pool-size", "app-value"));

        MpConfig sysPropOnly = (MpConfig) MpConfig.builder()
                                                  .withSources(appSource)
                                                  .build();

        assertValue("app.key", "app-value", sysPropOnly);
        assertValue("com.acme.size", "app-value", sysPropOnly);
        assertValue("server.executor-service.max-pool-size", "app-value", sysPropOnly);

        MpConfig merged = (MpConfig) MpConfig.builder()
                                             .withSources(new MpcSourceEnvironmentVariables())
                                             .withSources(appSource)
                                             .build();

        assertValue("app.key", "app-value", merged);
        assertValue("com.acme.size", "mapped-env-value", merged);
        assertValue("server.executor-service.max-pool-size", "mapped-env-value", merged);
    }

    static ConfigSource toConfigSource(final Map<String, String> map) {
        return new ConfigSource() {
            @Override
            public Map<String, String> getProperties() {
                return map;
            }

            @Override
            public String getValue(String propertyName) {
                return map.get(propertyName);
            }

            @Override
            public String getName() {
                return "helidon:unit-test";
            }
        };
    }

    static void assertValue(final String key, final String expectedValue, final MpConfig config) {
        assertThat(config.getValue(key, String.class), Matchers.is(expectedValue));
    }
}
