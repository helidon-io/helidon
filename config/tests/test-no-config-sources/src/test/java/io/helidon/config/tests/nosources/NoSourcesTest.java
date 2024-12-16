/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.config.tests.nosources;

import java.util.List;
import java.util.Optional;

import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class NoSourcesTest {
    @Test
    public void testJust() {
        Config config = Config.just();

        Optional<String> value = config.get("value")
                .asString()
                .asOptional();

        assertThat("We have used Config.just(), there should be NO config source", value, is(optionalEmpty()));
    }

    @Test
    public void testBuilder() {
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .sources(List.of())
                .build();

        Optional<String> value = config.get("value")
                .asString()
                .asOptional();

        assertThat("We have used Config.builder() without specifying any source, there should be NO config source",
                   value,
                   is(optionalEmpty()));
    }

    @Test
    public void testMetaConfigExplicit() {
        // a sanity check that meta configuration works when requested

        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .metaConfig()
                .build();

        Optional<String> value = config.get("value")
                .asString()
                .asOptional();

        assertThat("We have used metaConfig(), there should be the inlined config source configured",
                   value,
                   optionalValue(is("from-meta-config")));
    }

    @Test
    public void testMetaConfigCreate() {
        // a sanity check that meta configuration works when requested

        Config config = Config.create();

        Optional<String> value = config.get("value")
                .asString()
                .asOptional();

        assertThat("We have used Config.create(), there should be the inlined config source configured",
                   value,
                   optionalValue(is("from-meta-config")));
    }
}
