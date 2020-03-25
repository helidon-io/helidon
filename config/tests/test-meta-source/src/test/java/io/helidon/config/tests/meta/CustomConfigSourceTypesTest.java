/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config.tests.meta;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigValues;
import io.helidon.config.MetaConfig;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.tests.module.meta1.MyConfigSource1;
import io.helidon.config.tests.module.meta2.MyConfigSource2;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/**
 * Tests custom config source type registration.
 * <ul>
 * <li>Custom config source types are loaded from more then one module.</li>
 * </ul>
 */
public class CustomConfigSourceTypesTest {

    private void testCustomType(String type, Class<? extends ConfigSource> sourceClass) {
        Config metaConfig = justFrom(ConfigSources.create(
                ObjectNode.builder()
                        .addValue("type", type)
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(MetaConfig::configSource).get().get(0);

        assertThat(source, is(instanceOf(sourceClass)));

        Config config = justFrom(source);

        assertThat(config.get("key1").asInt(), is(ConfigValues.simpleValue(23)));
        assertThat(config.get("enabled").asBoolean(), is(ConfigValues.simpleValue(true)));
    }

    @Test
    public void testCustomTypeClass1() {
        testCustomType("meta1class", MyConfigSource1.class);
    }

    @Test
    public void testCustomTypeClass2() {
        testCustomType("meta2class", MyConfigSource2.class);
    }

    static Config justFrom(ConfigSource source) {
        return Config.builder(source)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
    }

}
