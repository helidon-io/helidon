/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ConfigSourceProvider;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class MultiConfigSourceProvider implements ConfigSourceProvider {

    public static final String TYPE = "unit-test-multi-source";

    @Override
    public boolean supports(String type) {
        return TYPE.equals(type);
    }

    @Override
    public ConfigSource create(String type, Config metaConfig) {
        fail("This should never be called. This should be tested as multi-source only.");
        return ConfigSources.empty();
    }

    @Override
    public Set<String> supported() {
        return Set.of(TYPE);
    }

    @Override
    public List<ConfigSource> createMulti(String type, Config metaConfig) {
        Optional<String> resourceOptional = metaConfig.get("resource").asString().asOptional();

        assertThat(resourceOptional, not(Optional.empty()));

        String resource = resourceOptional.get();

        assertThat("Reference to another key should have been resolved", resource, not("${source}"));

        return List.of(ConfigSources.empty());
    }
}
