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

package io.helidon.integrations.langchain4j.providers.jlama;

import java.nio.file.Path;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.testing.junit5.Testing;

import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Testing.Test
class JlamaTest {

    @Test
    void testChatModelRoot() {
        var config = JlamaChatModelConfig.create(Config.just(ConfigSources.classpath("application.yaml"))
                                                         .get(JlamaChatModelConfig.CONFIG_ROOT));

        assertThat(config, is(notNullValue()));
        assertThat(config.modelName(), is("TinyLlama/TinyLlama-1.1B-Chat-v0.6"));
        assertThat(config.temperature(), is(Optional.of(0.1F)));
        assertThat(config.workingQuantizedType(), is(Optional.of(DType.BF16)));
        assertThat(config.modelCachePath().map(Path::toString), is(Optional.of("/test/cache")));
        assertThat(config.workingDirectory().map(Path::toString), is(Optional.of("/tmp/test/dir")));
        assertThat(config.authToken(), is(Optional.of("test-token")));
        assertThat(config.maxTokens(), is(Optional.of(20)));
        assertThat(config.threadCount(), is(Optional.of(5)));
        assertThat(config.quantizeModelAtRuntime(), is(Optional.of(true)));
    }

    @Test
    void testStreamingChatModelRoot() {
        var config = JlamaStreamingChatModelConfig.create(Config.just(ConfigSources.classpath("application.yaml"))
                                                         .get(JlamaStreamingChatModelConfig.CONFIG_ROOT));

        assertThat(config, is(notNullValue()));
        assertThat(config.modelName(), is("TinyLlama/TinyLlama-1.1B-Chat-v0.6"));
        assertThat(config.temperature(), is(Optional.of(0.1F)));
        assertThat(config.workingQuantizedType(), is(Optional.of(DType.BF16)));
        assertThat(config.modelCachePath().map(Path::toString), is(Optional.of("/test/cache")));
        assertThat(config.workingDirectory().map(Path::toString), is(Optional.of("/tmp/test/dir")));
        assertThat(config.authToken(), is(Optional.of("test-token")));
        assertThat(config.maxTokens(), is(Optional.of(20)));
        assertThat(config.threadCount(), is(Optional.of(5)));
        assertThat(config.quantizeModelAtRuntime(), is(Optional.of(true)));
    }

    @Test
    void testLanguageModelRoot() {
        var config = JlamaLanguageModelConfig.create(Config.just(ConfigSources.classpath("application.yaml"))
                                                         .get(JlamaLanguageModelConfig.CONFIG_ROOT));

        assertThat(config, is(notNullValue()));
        assertThat(config.modelName(), is("TinyLlama/TinyLlama-1.1B-Chat-v0.6"));
        assertThat(config.temperature(), is(Optional.of(0.1F)));
        assertThat(config.workingQuantizedType(), is(Optional.of(DType.BF16)));
        assertThat(config.modelCachePath().map(Path::toString), is(Optional.of("/test/cache")));
        assertThat(config.workingDirectory().map(Path::toString), is(Optional.of("/tmp/test/dir")));
        assertThat(config.authToken(), is(Optional.of("test-token")));
        assertThat(config.maxTokens(), is(Optional.of(20)));
        assertThat(config.threadCount(), is(Optional.of(5)));
        assertThat(config.quantizeModelAtRuntime(), is(Optional.of(true)));
    }

    @Test
    void testEmbeddingModelRoot() {
        var config = JlamaEmbeddingModelConfig.create(Config.just(ConfigSources.classpath("application.yaml"))
                                                         .get(JlamaEmbeddingModelConfig.CONFIG_ROOT));

        assertThat(config, is(notNullValue()));
        assertThat(config.modelName(), is("TinyLlama/TinyLlama-1.1B-Chat-v0.6"));
        assertThat(config.modelCachePath().map(Path::toString), is(Optional.of("/test/cache")));
        assertThat(config.workingDirectory().map(Path::toString), is(Optional.of("/tmp/test/dir")));
        assertThat(config.authToken(), is(Optional.of("test-token")));
        assertThat(config.threadCount(), is(Optional.of(5)));
        assertThat(config.quantizeModelAtRuntime(), is(Optional.of(true)));
        assertThat(config.poolingType(), is(Optional.of(Generator.PoolingType.MODEL)));
    }
}