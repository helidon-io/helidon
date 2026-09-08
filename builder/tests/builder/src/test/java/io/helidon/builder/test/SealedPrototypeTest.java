/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;

import io.helidon.builder.test.testsubjects.SealedConfig;
import io.helidon.builder.test.testsubjects.SealedRuntime;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SealedPrototypeTest {
    @Test
    void testSealedPrototypeFeatures() {
        Class<?>[] permittedSubclasses = SealedConfig.class.getPermittedSubclasses();
        assertThat(SealedConfig.class.isSealed(), is(true));
        assertThat(permittedSubclasses.length, is(1));
        assertThat(Modifier.isFinal(permittedSubclasses[0].getModifiers()), is(true));

        SealedConfig<String> generatedFactory = SealedConfig.create("factory");
        assertThat(generatedFactory.value(), is("factory"));

        SealedRuntime<String> runtime = SealedConfig.<String>builder()
                .value("runtime")
                .build();
        assertThat(runtime.prototype().value(), is("runtime"));

        SealedConfig<String> configured = SealedConfig.create(
                Config.create(ConfigSources.create(Map.of("value", "configured"))));
        assertThat(configured.value(), is("configured"));

        SealedConfig<String> custom = SealedConfig.createCustom("custom");
        assertThat(custom.value(), is("custom"));
        assertThat(permittedSubclasses[0].isInstance(custom), is(true));

        boolean extendsBlueprint = Arrays.stream(SealedConfig.class.getInterfaces())
                .anyMatch(it -> it.getSimpleName().endsWith("Blueprint"));
        assertThat(extendsBlueprint, is(false));
    }
}
