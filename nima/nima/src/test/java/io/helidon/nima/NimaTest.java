/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima;

import java.util.Optional;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.pico.testing.PicoTestingSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

// this test is ordered, as we want to first try without an explicit config, and then with it
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NimaTest {
    @AfterEach
    void reset() {
        PicoTestingSupport.resetAll();
    }
    @Order(0)
    @Test
    void testConfig() {
        // value should be overridden using our custom config source
        Config config = Helidon.services()
                .lookup(Config.class)
                .get();

        assertThat(config.get("app.value").asString().asOptional(), is(Optional.of("source")));
    }

    @Order(1)
    @Test
    void testExplicitConfig() {
        // value should use the config as we provided it
        GlobalConfig.config(io.helidon.config.Config::create, true);

        Config config = Helidon.services()
                .lookup(Config.class)
                .get();

        assertThat(config.get("app.value").asString().asOptional(), is(Optional.of("file")));
    }
}
