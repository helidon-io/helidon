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

package io.helidon.builder.tests.wildcard;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;

class WildcardTest {
    @Test
    void testWildcardApi() {
        Wildcard wildcard = Wildcard.builder()
                .addExtension(WildcardTest.class)
                .plugin(String.class)
                .build();

        assertThat(wildcard.plugin(), CoreMatchers.<Class<?>>is(String.class));
        assertThat(wildcard.extensions(), hasItems(WildcardTest.class));
    }

    @Test
    void testWildcardConfig() {
        Config config = Config.just(ConfigSources.classpath("wildcards.yaml"));

        Wildcard wildcard = Wildcard.builder()
                .config(config.get("wildcards"))
                .build();

        assertThat(wildcard.plugin(), CoreMatchers.<Class<?>>is(WildcardTest.class));
        assertThat(wildcard.extensions(), hasItems(WildcardTest.class,
                                                   String.class));
    }
}
