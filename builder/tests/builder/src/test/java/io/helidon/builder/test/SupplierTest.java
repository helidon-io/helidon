/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import io.helidon.builder.test.testsubjects.SupplierBean;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.EventConfigSource;
import io.helidon.config.spi.NodeConfigSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SupplierTest {
    private static final String KEY = "string-supplier";
    private static final String ORIGINAL_VALUE = "value";
    private static final String NEW_VALUE = "new-value";

    @Test
    void testChangeString() {
        TestSource testSource = new TestSource();
        Config config = Config.just(testSource);
        SupplierBean b = SupplierBean.create(config);

        Supplier<String> stringSupplier = b.stringSupplier();
        Supplier<char[]> arraySupplier = b.charSupplier();

        assertThat(stringSupplier.get(), is(ORIGINAL_VALUE));
        assertThat(arraySupplier.get(), is(ORIGINAL_VALUE.toCharArray()));

        testSource.update();

        assertThat(stringSupplier.get(), is(NEW_VALUE));
        assertThat(arraySupplier.get(), is(NEW_VALUE.toCharArray()));
    }



    private static class TestSource implements ConfigSource, EventConfigSource, NodeConfigSource {

        private BiConsumer<String, ConfigNode> consumer;

        @Override
        public void onChange(BiConsumer<String, ConfigNode> changedNode) {
            this.consumer = changedNode;
        }

        @Override
        public Optional<ConfigContent.NodeContent> load() throws ConfigException {
            return Optional.of(ConfigContent.NodeContent.builder()
                                       .node(ConfigNode.ObjectNode.builder()
                                                     .addValue(KEY, ORIGINAL_VALUE)
                                                     .build())
                                       .build());
        }

        void update() {
            consumer.accept(KEY, ConfigNode.ValueNode.create(NEW_VALUE));
        }
    }
}
