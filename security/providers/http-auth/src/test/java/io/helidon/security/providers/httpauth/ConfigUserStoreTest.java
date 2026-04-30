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

import java.util.Set;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigUserStoreTest {
    @Test
    @SuppressWarnings("removal")
    void defaultConstructorUsesNonNullValues() {
        ConfigUserStore.ConfigUser user = new ConfigUserStore.ConfigUser();

        assertThat(user.login(), is(""));
        assertThat(user.isPasswordValid(new char[0]), is(true));
        assertThat(user.isPasswordValid(null), is(false));
        assertThat(user.roles().isEmpty(), is(true));
    }

    @Test
    void createUsesConfiguredValues() {
        Config config = Config.just("""
                user:
                  login: "jack"
                  password: "secret"
                  roles: ["admin", "user"]
                """, MediaTypes.APPLICATION_YAML)
                .get("user");

        ConfigUserStore.ConfigUser user = ConfigUserStore.ConfigUser.create(config);

        assertThat(user.login(), is("jack"));
        assertThat(user.isPasswordValid("secret".toCharArray()), is(true));
        assertThat(user.roles(), is(Set.of("admin", "user")));
    }
}
