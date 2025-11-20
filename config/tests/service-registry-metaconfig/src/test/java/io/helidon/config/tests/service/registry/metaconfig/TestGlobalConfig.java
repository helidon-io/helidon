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

package io.helidon.config.tests.service.registry.metaconfig;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.MapConfigSource;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test(perMethod = true)
public class TestGlobalConfig {

    @Test
    void one() {
        Services.set(Config.class, Config.just(MapConfigSource.create(Map.of("key", "one"))));
        assertThat(Config.global().get("key").asString().get(), is("one"));
    }

    @Test
    void two() {
        Services.set(Config.class, Config.just(MapConfigSource.create(Map.of("key", "two"))));
        assertThat(Config.global().get("key").asString().get(), is("two"));;
    }

    @Test
    void three() {
        Services.set(Config.class, Config.just(MapConfigSource.create(Map.of("key", "three"))));
        assertThat(Config.global().get("key").asString().get(), is("three"));
    }


    @Test
    void oneServices() {
        Services.set(Config.class, Config.just(MapConfigSource.create(Map.of("key", "one"))));
        assertThat(Services.get(Config.class).get("key").asString().get(), is("one"));
    }

    @Test
    void twoServices() {
        Services.set(Config.class, Config.just(MapConfigSource.create(Map.of("key", "two"))));
        assertThat(Services.get(Config.class).get("key").asString().get(), is("two"));;
    }

    @Test
    void threeServices() {
        Services.set(Config.class, Config.just(MapConfigSource.create(Map.of("key", "three"))));
        assertThat(Services.get(Config.class).get("key").asString().get(), is("three"));
    }
}
