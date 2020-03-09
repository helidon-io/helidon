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

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.RetryPolicy;
import io.helidon.config.spi.WatchableSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link AbstractSource}.
 */
public class AbstractSourceTest {
    @Test
    public void testHasChanged() {
        Instant now = Instant.now();

        TestingSource testingSource = TestingSource.builder()
                .stamp(now)
                .build();

        Instant older = Instant.EPOCH;
        assertThat("Source should have newer data, and report modified", testingSource.isModified(older), is(true));

        Instant newer = Instant.MAX;
        assertThat("Source should have older data, report and unmodified", testingSource.isModified(newer), is(false));

        assertThat("Source should have the same data, and report unmodified", testingSource.isModified(now), is(false));
    }

    @Test
    public void testInitAll() {
        TestingSource.TestingBuilder builder = TestingSource.builder().config(
                Config.builder(ConfigSources.create(
                        Map.of("optional", "true")))
                        .addMapper(TestingRetryPolicy.class, config -> new TestingRetryPolicy())
                        .addMapper(TestingPollingStrategy.class, config -> new TestingPollingStrategy())
                        .build())
                .pollingStrategy(new TestingPollingStrategy())
                .retryPolicy(new TestingRetryPolicy());

        //optional
        assertThat(builder.isOptional(), is(true));
        //polling-strategy
        assertThat(builder.pollingStrategy().get(), is(instanceOf(TestingPollingStrategy.class)));
        //retry-policy
        assertThat(builder.retryPolicy().get(), is(instanceOf(TestingRetryPolicy.class)));
    }

    @Test
    public void testInitNothing() {
        TestingSource.TestingBuilder builder = TestingSource.builder().config(Config.empty());

        //optional
        assertThat(builder.isOptional(), is(false));
        //polling-strategy
        assertThat(builder.pollingStrategy(), is(Optional.empty()));
        assertThat(builder.retryPolicy(), is(Optional.empty()));
    }

    private static class TestingSource extends AbstractSource
            implements WatchableSource<String>,
                       PollableSource<Instant> {

        private final Instant stamp;

        TestingSource(TestingBuilder builder) {
            super(builder);
            this.stamp = builder.stamp;
        }

        static TestingBuilder builder() {
            return new TestingSource.TestingBuilder();
        }

        @Override
        public boolean isModified(Instant stamp) {
            return stamp.isBefore(this.stamp);
        }

        @Override
        public String target() {
            return "test-source";
        }

        @Override
        public Optional<PollingStrategy> pollingStrategy() {
            return super.pollingStrategy();
        }

        @Override
        public Optional<ChangeWatcher<Object>> changeWatcher() {
            return super.changeWatcher();
        }

        private static class TestingBuilder extends AbstractSourceBuilder<TestingBuilder, String>
                implements PollableSource.Builder<TestingBuilder>,
                           WatchableSource.Builder<TestingBuilder, String>,
                           io.helidon.common.Builder<TestingSource> {

            private Instant stamp;

            private TestingBuilder() {
            }

            @Override
            public TestingBuilder changeWatcher(ChangeWatcher<String> changeWatcher) {
                return super.changeWatcher(changeWatcher);
            }

            @Override
            public TestingBuilder pollingStrategy(PollingStrategy pollingStrategy) {
                return super.pollingStrategy(pollingStrategy);
            }

            @Override
            public TestingSource build() {
                return new TestingSource(this);
            }

            public TestingBuilder stamp(Instant stamp) {
                this.stamp = stamp;
                return this;
            }
        }
    }

    public static class TestingPollingStrategy implements PollingStrategy {
        @Override
        public void start(Polled polled) {
        }
    }

    public static class TestingRetryPolicy implements RetryPolicy {
        @Override
        public <T> T execute(Supplier<T> call) {
            return call.get();
        }
    }

}
