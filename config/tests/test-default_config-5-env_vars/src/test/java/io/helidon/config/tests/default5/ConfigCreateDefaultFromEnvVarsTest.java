/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.tests.default5;

import io.helidon.config.Config;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.is;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests {@link Config#create()} and ENV VAR is used first, has top priority.
 */
public class ConfigCreateDefaultFromEnvVarsTest {

    private static final String KEY = "value";
    private static final String PROP_VALUE = "sys-prop";
    private static final String ENV_VALUE = "sys-prop"; //set in pom.xml, maven-surefire-plugin, environmentVariables

    @Test
    @ExtendWith(RestoreSystemPropertiesExt.class)
    public void testCreate() {
        Config config = Config.create();

        assertThat(config.get(KEY).asString(), is(ENV_VALUE));
    }

    @Test
    public void testCreateWithSysPropSet() {
        System.setProperty(KEY, PROP_VALUE);

        Config config = Config.create();

        assertThat(config.get(KEY).asString(), is(ENV_VALUE));
    }

}
