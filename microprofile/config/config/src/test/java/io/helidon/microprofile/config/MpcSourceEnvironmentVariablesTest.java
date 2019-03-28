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
import java.util.NoSuchElementException;

import io.helidon.config.test.infra.RestoreSystemPropertiesExt;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.common.CollectionsHelper.mapOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link MpcSourceEnvironmentVariables}.
 */
@ExtendWith(RestoreSystemPropertiesExt.class)
public class MpcSourceEnvironmentVariablesTest {

    @Test
    public void testEnvironmentVariableAliases() {
        MpConfig config = (MpConfig) MpConfig.builder()
                                             .withSources(new MpcSourceEnvironmentVariables())
                                             .build();

        assertValue("simple", "unmapped-env-value", config);

        assertValue("_underscore", "mapped-env-value", config);
        assertValue("/underscore", "mapped-env-value", config);

        assertValue("FOO_BAR", "mapped-env-value", config);
        assertValue("foo.bar", "mapped-env-value", config);
        assertValue("foo/bar", "mapped-env-value", config);
        assertValue("foo#bar", "mapped-env-value", config);

        assertValue("com_ACME_size", "mapped-env-value", config);
        assertValue("com.ACME.size", "mapped-env-value", config);
        assertValue("com!ACME@size", "mapped-env-value", config);
        assertValue("com#ACME$size", "mapped-env-value", config);
        assertValue("com/ACME/size", "mapped-env-value", config);

        assertValue("SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE", "mapped-env-value", config);
        assertValue("SERVER.EXECUTOR-SERVICE.MAX-POOL-SIZE", "mapped-env-value", config);
        assertValue("server.executor-service.max-pool-size", "mapped-env-value", config);
        assertValue("SERVER_EXECUTOR_dash_SERVICE_MAX_dash_POOL_dash_SIZE", "mapped-env-value", config);
    }

    @Test
    public void testPrecedence() {

        // NOTE: This code should be kept in sync with ConfigSourcesTest.testPrecedence(), as we want SE and MP to be as
        //       symmetrical as possible.

        System.setProperty("com.ACME.size", "sys-prop-value");

        final ConfigSource appSource = toConfigSource(mapOf("app.key", "app-value",
                                                            "com.ACME.size", "app-value",
                                                            "server.executor-service.max-pool-size", "app-value"));
        // Application source only.
        // Key mapping should NOT occur.

        MpConfig appOnly = (MpConfig) MpConfig.builder()
                                              .withSources(appSource)
                                              .build();

        assertValue("app.key", "app-value", appOnly);
        assertValue("com.ACME.size", "app-value", appOnly);
        assertValue("server.executor-service.max-pool-size", "app-value", appOnly);

        assertNoValue("com.acme.size", appOnly);
        assertNoValue("com/ACME/size", appOnly);
        assertNoValue("server/executor-service/max-pool-size", appOnly);


        // Application and system property sources.
        // System properties should take precedence over application values.
        // Key mapping should NOT occur.

        MpConfig appAndSys = (MpConfig) MpConfig.builder()
                                                .withSources(appSource)
                                                .withSources(new MpcSourceSystemProperties())
                                                .build();

        assertValue("app.key", "app-value", appAndSys);
        assertValue("com.ACME.size", "sys-prop-value", appAndSys);
        assertValue("server.executor-service.max-pool-size", "app-value", appAndSys);

        assertNoValue("com.acme.size", appOnly);
        assertNoValue("com/ACME/size", appAndSys);
        assertNoValue("server/executor-service/max-pool-size", appAndSys);


        // Application and environment variable sources.
        // Environment variables should take precedence over application values.
        // Key mapping SHOULD occur.

        MpConfig appAndEnv = (MpConfig) MpConfig.builder()
                                                .withSources(appSource)
                                                .withSources(new MpcSourceEnvironmentVariables())
                                                .build();

        assertValue("app.key", "app-value", appAndEnv);
        assertValue("com.ACME.size", "mapped-env-value", appAndEnv);
        assertValue("server.executor-service.max-pool-size", "mapped-env-value", appAndEnv);

        assertNoValue("com.acme.size", appAndEnv);
        assertValue("com/ACME/size", "mapped-env-value", appAndEnv);
        assertValue("server/executor-service/max-pool-size", "mapped-env-value", appAndEnv);


        // Application, system property and environment variable sources.
        // System properties should take precedence over environment variables.
        // Environment variables should take precedence over application values.
        // Key mapping SHOULD occur.

        MpConfig appSysAndEnv = (MpConfig) MpConfig.builder()
                                                   .withSources(appSource)
                                                   .withSources(new MpcSourceEnvironmentVariables())
                                                   .withSources(new MpcSourceSystemProperties())
                                                   .build();

        assertValue("app.key", "app-value", appSysAndEnv);
        assertValue("com.ACME.size", "sys-prop-value", appSysAndEnv);
        assertValue("server.executor-service.max-pool-size", "mapped-env-value", appSysAndEnv);

        assertNoValue("com.acme.size", appAndEnv);
        assertValue("com/ACME/size", "mapped-env-value", appSysAndEnv);
        assertValue("server/executor-service/max-pool-size", "mapped-env-value", appSysAndEnv);
    }

    static void assertValue(final String key, final String expectedValue, final MpConfig config) {
        assertThat(config.getValue(key, String.class), is(expectedValue));
    }

    static void assertNoValue(final String key, final MpConfig config) {
        assertThrows(NoSuchElementException.class, () -> config.getValue(key, String.class));
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
}
