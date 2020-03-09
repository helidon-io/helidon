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

package io.helidon.config;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link ConfigMappers} with focus on failing mapping.
 * It checks that built-in mappers throw exception in case of wrong format value.
 */
public class ConfigMappersFailingTest {

    public static Stream<Class<?>> builtInMapperTypes() {
        return ConfigMappers.BUILT_IN_MAPPERS.keySet()
                .stream()
                .filter(cls -> !Boolean.class.equals(cls)) //'Boolean.parseBoolean' does NOT fail
                .filter(cls -> !File.class.equals(cls)) //'new File' does NOT fail
                .filter(cls -> !Path.class.equals(cls)) //'Paths.get' does NOT fail
                .filter(cls -> !Map.class.equals(cls)) //'Map' can bew created from any value
                .filter(cls -> !Properties.class.equals(cls)) //'Properties' can bew created from any value
                ;
    }

    @ParameterizedTest
    @MethodSource("builtInMapperTypes")
    public void testMappingFails(Class<?> type) {
        ConfigMapperManager manager = BuilderImpl.buildMappers(ConfigMapperManager.MapperProviders.create());

        String key = "config.key.with.wrong.format";
        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of(key, ") bad, bad value ")))
                .build();

        ConfigMappingException ex = assertThrows(ConfigMappingException.class, () -> {
            manager.map(config.get(key), type);
        });
        assertThat(ex.getMessage(), containsString(key));
    }

}
