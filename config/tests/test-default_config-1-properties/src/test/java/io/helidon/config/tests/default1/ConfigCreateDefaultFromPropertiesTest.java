/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.tests.default1;

import io.helidon.config.Config;
import io.helidon.config.ConfigValues;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link Config#create()} with just built-in Properties Parser available, missing other parsers.
 */
public class ConfigCreateDefaultFromPropertiesTest {

    private static final String KEY = "value";
    private static final String CONFIG_VALUE = "PROPERTIES";
    private static final String PROP_VALUE = "sys-prop";

    @Test
    @ExtendWith(RestoreSystemPropertiesExt.class)
    public void testCreate() {
        Config config = Config.create();

        assertThat(config.get(KEY).asString().get(), is(CONFIG_VALUE));
    }

    @Test
    @ExtendWith(RestoreSystemPropertiesExt.class)
    public void testCreateKeyFromSysProps() {
        System.setProperty(KEY, PROP_VALUE);

        Config config = Config.create();

        assertThat(config.get(KEY).asString(), is(ConfigValues.simpleValue(PROP_VALUE)));
    }

}
