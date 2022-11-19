/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.config.test;

import java.util.Optional;

import io.helidon.common.config.Config;

import org.junit.jupiter.api.Test;

import static io.helidon.common.config.ConfigHolder.config;
import static io.helidon.common.config.ConfigHolder.reset;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ConfigHolderTest {

    @Test
    void testConfig() {
        Optional<Config> cfg = config();
        assertThat(cfg.orElseThrow().asString().get(), is("mock"));

        Config mockCfg = mock(Config.class);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> config(mockCfg));
        assertThat(e.getMessage(),
                   equalTo(Config.class.getSimpleName() + " already set"));

        reset();
        config(mockCfg);
        assertThat(config().orElseThrow(), sameInstance(mockCfg));
    }

}
