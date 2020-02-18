/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.config.spi.ConfigContent.NodeContent;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.NodeConfigSource;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link RetryPolicies}.
 */
public class RetryPoliciesTest {

    @Test
    public void repeat() throws Exception {
        CountDownLatch loadLatch = new CountDownLatch(3);
        NodeConfigSource source = TestingSource.builder()
                .retryPolicy(RetryPolicies.repeat(2)
                                     .delay(Duration.ZERO))
                .loadLatch(loadLatch)
                .dataSupplier(() -> {
                    throw new RuntimeException();
                })
                .build();

        BuilderImpl.ConfigContextImpl context = Mockito.mock(BuilderImpl.ConfigContextImpl.class);

        ConfigSourceRuntimeImpl csr = new ConfigSourceRuntimeImpl(context, source);
        assertThrows(ConfigException.class, csr::load);

        assertThat(loadLatch.await(50, TimeUnit.MILLISECONDS), is(true));
    }

    @Test
    public void testDefaultRetryPolicy() throws Exception {
        CountDownLatch loadLatch = new CountDownLatch(1);
        NodeConfigSource source = TestingSource.builder()
                .loadLatch(loadLatch)
                .build();

        BuilderImpl.ConfigContextImpl context = Mockito.mock(BuilderImpl.ConfigContextImpl.class);
        ConfigSourceRuntimeImpl csr = new ConfigSourceRuntimeImpl(context, source);
        Optional<ObjectNode> load = csr.load();
        assertThat(load, not(Optional.empty()));

        assertThat(loadLatch.await(50, TimeUnit.MILLISECONDS), is(true));
    }

    @Test
    public void testJustCallRetryPolicy() throws Exception {
        CountDownLatch loadLatch = new CountDownLatch(1);
        NodeConfigSource source = TestingSource.builder()
                .retryPolicy(RetryPolicies.justCall())
                .loadLatch(loadLatch)
                .build();

        BuilderImpl.ConfigContextImpl context = Mockito.mock(BuilderImpl.ConfigContextImpl.class);
        ConfigSourceRuntimeImpl csr = new ConfigSourceRuntimeImpl(context, source);
        Optional<ObjectNode> load = csr.load();
        assertThat(load, not(Optional.empty()));

        assertThat(loadLatch.await(50, TimeUnit.MILLISECONDS), is(true));
    }

    private static class TestingSource extends AbstractConfigSource
            implements NodeConfigSource {

        private final Supplier<NodeContent> dataSupplier;
        private final CountDownLatch loadLatch;

        TestingSource(TestingBuilder builder) {
            super(builder);
            this.loadLatch = builder.loadLatch;
            this.dataSupplier = builder.dataSupplier;
        }

        static TestingBuilder builder() {
            return new TestingBuilder();
        }

        @Override
        public Optional<NodeContent> load() throws ConfigException {
            loadLatch.countDown();
            return Optional.of(dataSupplier.get());
        }

        private static class TestingBuilder extends AbstractConfigSourceBuilder<TestingBuilder, Void>
                implements io.helidon.common.Builder<TestingSource> {

            public CountDownLatch loadLatch;
            private Supplier<NodeContent> dataSupplier = () -> NodeContent.builder()
                    .node(ObjectNode.empty())
                    .get();

            TestingBuilder() {
            }

            TestingBuilder loadLatch(CountDownLatch loadLatch) {
                this.loadLatch = loadLatch;
                return this;
            }

            TestingBuilder dataSupplier(Supplier<NodeContent> dataSupplier) {
                this.dataSupplier = dataSupplier;
                return this;
            }

            @Override
            public TestingSource build() {
                return new TestingSource(this);
            }
        }
    }

}
