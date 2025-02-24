/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.testing.junit5;

import java.util.Map;

import jakarta.inject.Inject;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.testing.AddConfigSource;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class TestAddConfigSource {

    @AddConfigSource
    static ConfigSource config() {
        return MpConfigSources.create(Map.of(
                "some.key", "some.value",
                "another.key", "another.value"));
    }

    @Inject
    @ConfigProperty(name = "some.key")
    private String someKey;

    @Inject
    @ConfigProperty(name = "another.key")
    private String anotherKey;

    @Test
    void testValue() {
        assertThat(someKey, is("some.value"));
        assertThat(anotherKey, is("another.value"));
    }
}
