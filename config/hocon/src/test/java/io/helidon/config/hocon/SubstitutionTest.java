/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.hocon;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import io.helidon.config.ClasspathConfigSource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

class SubstitutionTest {

    @Test
    void testSubstitution() {
        Config config = Config.create(
                ClasspathConfigSource.create("conf/substitution1.conf"),
                ClasspathConfigSource.create("conf/substitution2.conf"));

        String value = config.get("app1.greeting1").asString().orElse(null);
        assertThat(value, is("Hello"));

        value = config.get("app1.greeting2").asString().orElse(null);
        assertThat(value, is("Hello"));

        value = config.get("app2.greeting2").asString().orElse(null);
        assertThat(value, is("Hello"));

        value = config.get("app2.greeting2").asString().orElse(null);
        assertThat(value, is("Hello"));

        List<String> list = config.get("app2.greetings").asList(String.class)
                .orElse(Collections.emptyList());
        assertThat(list, Matchers.<Collection<String>> allOf(
                hasSize(2), hasItem(is("Hello"))));
    }

    @Test
    void testHoconExpansion() {
        Config config = Config.create(
                ConfigSources.create("foo: ${VAR1}/bar", "application/hocon"));
        assertThat(config.get("foo").asString().orElseThrow(NoSuchElementException::new), is("foo/bar"));
    }

    @Test
    void testHoconExpansionQuotes() {
        Config config = Config.create(
                ConfigSources.create("foo: \"${VAR1}/bar\"", "application/hocon"));
        assertThat(config.get("foo").asString().orElseThrow(NoSuchElementException::new), is("foo/bar"));
    }
}
