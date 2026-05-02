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

package io.helidon.security.providers.httpauth;

import java.util.Map;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigUserStoreTest {

    @Test
    void configUserValidatesPassword() {
        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of("login", "jack",
                                                     "password", "jackIsGreat")))
                .build();

        ConfigUserStore.ConfigUser user = ConfigUserStore.ConfigUser.create(config);

        assertThat(user.login(), is("jack"));
        assertThat(user.roles(), is(Set.of()));
        assertThat(user.isPasswordValid("jackIsGreat".toCharArray()), is(true));
        assertThat(user.isPasswordValid("jackIsGraet".toCharArray()), is(false));
        assertThat(user.isPasswordValid("short".toCharArray()), is(false));
        assertThat(user.isPasswordValid("jackIsGreater".toCharArray()), is(false));
        assertThat(user.isPasswordValid("".toCharArray()), is(false));
        assertThat(user.isPasswordValid(null), is(false));
    }

    @Test
    void configUserValidatesEmptyPassword() {
        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of("login", "jill")))
                .build();

        ConfigUserStore.ConfigUser user = ConfigUserStore.ConfigUser.create(config);

        assertThat(user.login(), is("jill"));
        assertThat(user.roles(), is(Set.of()));
        assertThat(user.isPasswordValid("".toCharArray()), is(true));
        assertThat(user.isPasswordValid("password".toCharArray()), is(false));
    }

    @SuppressWarnings("removal")
    @Test
    void configUserDefaultConstructorKeepsEmptyPassword() {
        ConfigUserStore.ConfigUser user = new ConfigUserStore.ConfigUser();

        assertThat(user.login(), is((String) null));
        assertThat(user.roles(), is(Set.of()));
        assertThat(user.isPasswordValid("".toCharArray()), is(true));
        assertThat(user.isPasswordValid("password".toCharArray()), is(false));
        assertThat(user.isPasswordValid(null), is(false));
    }
}
