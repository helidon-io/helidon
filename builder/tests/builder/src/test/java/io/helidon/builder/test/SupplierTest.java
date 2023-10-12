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

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SupplierTest {
    private static final String KEY = "string-supplier";
    private static final String KEY_OPTIONAL = "optional-supplier";
    private static final String ORIGINAL_VALUE = "value";
    private static final char[] ORIGINAL_VALUE_CHARS = ORIGINAL_VALUE.toCharArray();
    private static final String NEW_VALUE = "new-value";
    private static final char[] NEW_VALUE_CHARS = NEW_VALUE.toCharArray();

    @Test
    void testChangeString() {
        TestSource testSource = new TestSource(ORIGINAL_VALUE);
        Config config = Config.just(testSource);
        SupplierBean b = SupplierBean.create(config);

        Supplier<String> stringSupplier = b.stringSupplier();
        Supplier<char[]> arraySupplier = b.charSupplier();
        Supplier<Optional<String>> optionalSupplier = b.optionalSupplier();
        Supplier<Optional<char[]>> optionalCharSupplier = b.optionalCharSupplier();

        assertThat(stringSupplier.get(), is(ORIGINAL_VALUE));
        assertThat(arraySupplier.get(), is(ORIGINAL_VALUE_CHARS));
        assertThat(optionalSupplier.get(), optionalValue(is(ORIGINAL_VALUE)));
        assertThat(optionalCharSupplier.get(), optionalValue(is(ORIGINAL_VALUE_CHARS)));

        testSource.update(NEW_VALUE);

        assertThat(stringSupplier.get(), is(NEW_VALUE));
        assertThat(arraySupplier.get(), is(NEW_VALUE_CHARS));
        assertThat(optionalSupplier.get(), optionalValue(is(NEW_VALUE)));
        assertThat(optionalCharSupplier.get(), optionalValue(is(NEW_VALUE_CHARS)));
    }

    @Test
    void testChangeOptionalStringFromEmpty() {
        TestSource testSource = new TestSource(null);
        Config config = Config.just(testSource);
        SupplierBean b = SupplierBean.create(config);

        Supplier<Optional<String>> optionalSupplier = b.optionalSupplier();
        Supplier<Optional<char[]>> optionalCharSupplier = b.optionalCharSupplier();

        assertThat(optionalSupplier.get(), optionalEmpty());
        assertThat(optionalCharSupplier.get(), optionalEmpty());

        testSource.update(NEW_VALUE);

        assertThat(optionalSupplier.get(), optionalValue(is(NEW_VALUE)));
        assertThat(optionalCharSupplier.get(), optionalValue(is(NEW_VALUE_CHARS)));
    }

    @Test
    void testChangeOptionalStringToEmpty() {
        TestSource testSource = new TestSource(ORIGINAL_VALUE);
        Config config = Config.just(testSource);
        SupplierBean b = SupplierBean.create(config);

        Supplier<Optional<String>> optionalSupplier = b.optionalSupplier();
        Supplier<Optional<char[]>> optionalCharSupplier = b.optionalCharSupplier();

        assertThat(optionalSupplier.get(), optionalValue(is(ORIGINAL_VALUE)));
        assertThat(optionalCharSupplier.get(), optionalValue(is(ORIGINAL_VALUE_CHARS)));

        testSource.update(null);

        assertThat(optionalSupplier.get(), optionalEmpty());
        assertThat(optionalCharSupplier.get(), optionalEmpty());
    }

    private static class TestSource implements ConfigSource, EventConfigSource, NodeConfigSource {

        private final String optionalValue;
        private BiConsumer<String, ConfigNode> consumer;

        private TestSource(String optionalValue) {
            this.optionalValue = optionalValue;
        }

        @Override
        public void onChange(BiConsumer<String, ConfigNode> changedNode) {
            this.consumer = changedNode;
        }

        @Override
        public Optional<ConfigContent.NodeContent> load() throws ConfigException {
            ConfigNode.ObjectNode.Builder rootNode = ConfigNode.ObjectNode.builder()
                    .addValue(KEY, ORIGINAL_VALUE);
            if (optionalValue != null) {
                rootNode.addValue(KEY_OPTIONAL, optionalValue);

            }
            return Optional.of(ConfigContent.NodeContent.builder()
                                       .node(rootNode.build())
                                       .build());
        }

        void update(String newOptionalValue) {
            ConfigNode.ObjectNode.Builder rootNode = ConfigNode.ObjectNode.builder()
                    .addValue(KEY, NEW_VALUE);
            if (newOptionalValue == null) {
                rootNode.addObject(KEY_OPTIONAL, ConfigNode.ObjectNode.empty());
            } else {
                rootNode.addValue(KEY_OPTIONAL, newOptionalValue);
            }

            consumer.accept("", rootNode.build());
        }
    }
}
