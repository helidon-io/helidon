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

package io.helidon.integrations.langchain4j.providers.coherence;

import io.helidon.common.config.Config;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Testing.Test
class CoherenceEmbeddingStorePropertiesTest {

    @Test
    void testDefaultRoot() {
        var config = CoherenceEmbeddingStoreConfig.create(Services.get(Config.class)
                                                               .get(CoherenceEmbeddingStoreConfig.CONFIG_ROOT));
        assertThat(config, is(notNullValue()));
        assertThat(config.enabled(), is(true));
        assertThat(config.session().isPresent(), is(true));
        assertThat(config.session().get(), is("session"));
        assertThat(config.name().isPresent(), is(true));
        assertThat(config.name().get(), is("namedMap"));
        assertThat(config.index().isPresent(), is(true));
        assertThat(config.index().get(), is("hnsw"));
        assertThat(config.dimension().isPresent(), is(true));
        assertThat(config.dimension().get(), is(768));
        assertThat(config.normalizeEmbeddings().isPresent(), is(true));
        assertThat(config.normalizeEmbeddings().get(), is(false));
    }
}
