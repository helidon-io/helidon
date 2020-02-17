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
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

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
        AbstractSource source = TestingSource.builder()
                .retryPolicy(RetryPolicies.repeat(2)
                                     .delay(Duration.ZERO))
                .loadLatch(loadLatch)
                .dataSupplier(() -> {
                    throw new RuntimeException();
                })
                .build();

        assertThrows(ConfigException.class, () -> {
            source.reload();
        });

        assertThat(loadLatch.await(50, TimeUnit.MILLISECONDS), is(true));

    }

    @Test
    public void testDefaultRetryPolicy() throws Exception {
        CountDownLatch loadLatch = new CountDownLatch(1);
        AbstractSource source = TestingSource.builder()
                .loadLatch(loadLatch)
                .build();

        source.reload();

        assertThat(loadLatch.await(50, TimeUnit.MILLISECONDS), is(true));
    }

    @Test
    public void testJustCallRetryPolicy() throws Exception {
        CountDownLatch loadLatch = new CountDownLatch(1);
        AbstractSource source = TestingSource.builder()
                .retryPolicy(RetryPolicies.justCall())
                .loadLatch(loadLatch)
                .build();

        source.reload();

        assertThat(loadLatch.await(50, TimeUnit.MILLISECONDS), is(true));
    }

    private static class TestingSource extends AbstractSource<String, Instant> {

        private final Supplier<Data<String>> dataSupplier;
        private CountDownLatch loadLatch;

        TestingSource(TestingBuilder builder) {
            super(builder);
            this.loadLatch = builder.loadLatch;
            this.dataSupplier = builder.dataSupplier;
        }

        static TestingBuilder builder() {
            return new TestingBuilder();
        }

        @Override
        protected Optional<Instant> dataStamp() {
            return Optional.empty();
        }

        @Override
        protected Data<String> loadData() throws ConfigException {
            loadLatch.countDown();
            return dataSupplier.get();
        }

        private static class TestingBuilder extends Builder<TestingBuilder, Void, TestingSource> {

            public CountDownLatch loadLatch;
            private Supplier<Data<String>> dataSupplier = () -> Data.create("nothing", Instant.now());

            TestingBuilder() {
                super(Void.class);
            }

            TestingBuilder loadLatch(CountDownLatch loadLatch) {
                this.loadLatch = loadLatch;
                return this;
            }

            TestingBuilder dataSupplier(Supplier<Data<String>> dataSupplier) {
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
