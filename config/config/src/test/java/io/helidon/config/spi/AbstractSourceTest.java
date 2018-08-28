/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.spi;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.reactive.Flow;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.config.PollingStrategies;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link AbstractSource}.
 */
public class AbstractSourceTest {

    private static final Optional<String> LAST_DATA = Optional.of("last data");
    private static final Optional<String> NEW_DATA = Optional.of("new data");
    private static final int TEST_DELAY_MS = 1;

    @Test
    public void testHasChanged() {
        TestingSource testingSource = TestingSource.builder().build();
        assertThat(testingSource.hasChanged(LAST_DATA, NEW_DATA), is(true));
    }

    @Test
    public void testHasChangedLastEmpty() {
        TestingSource testingSource = TestingSource.builder().build();
        assertThat(testingSource.hasChanged(Optional.empty(), NEW_DATA), is(true));
    }

    @Test
    public void testHasChangedNewEmpty() {
        TestingSource testingSource = TestingSource.builder().build();
        assertThat(testingSource.hasChanged(LAST_DATA, Optional.empty()), is(true));
    }

    @Test
    public void testHasChangedIsSame() {
        TestingSource testingSource = TestingSource.builder().build();
        assertThat(testingSource.hasChanged(LAST_DATA, LAST_DATA), is(false));
    }

    @Test
    public void testHasChangedBothEmpty() {
        TestingSource testingSource = TestingSource.builder().build();
        assertThat(testingSource.hasChanged(Optional.empty(), Optional.empty()), is(false));
    }

    @Test
    public void testLoadDataChangedSinceLastLoad() throws InterruptedException {
        TestingSource testingSource = TestingSource.builder()
                .dataSupplier(() -> new AbstractSource.Data<>(Optional.of(Instant.now().toString()), Optional.of(Instant.now())))
                .build();

        Optional<AbstractSource.Data<String, Instant>> stringData = testingSource.loadDataChangedSinceLastLoad();
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure the timestamps will differ.
        assertThat(stringData.get().stamp(), is(not(testingSource.loadDataChangedSinceLastLoad().get().stamp())));
    }

    @Test
    public void testReload() throws InterruptedException {
        CountDownLatch eventFired = new CountDownLatch(2);
        TestingSource testingSource = TestingSource.builder()
                .dataSupplier(() -> new AbstractSource.Data<>(Optional.of(Instant.now().toString()), Optional.of(Instant.now())))
                .fireEventRunnable(eventFired::countDown)
                .build();

        testingSource.reload();
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS);
        testingSource.reload();

        assertThat(eventFired.await(50, TimeUnit.MILLISECONDS), is(true));
    }

    @Test
    public void testRetryPolicy() throws InterruptedException {
        CountDownLatch methodCalled = new CountDownLatch(1);
        TestingSource testingSource = TestingSource.builder()
                .dataSupplier(() -> new AbstractSource.Data<>(Optional.of(Instant.now().toString()), Optional.of(Instant.now())))
                .fireEventRunnable(() -> {
                })
                .retryPolicy(new RetryPolicy() {
                    @Override
                    public <T> T execute(Supplier<T> call) {
                        methodCalled.countDown();
                        return call.get();
                    }

                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        return false;
                    }
                })
                .build();

        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        testingSource.reload();

        assertThat(methodCalled.await(50, TimeUnit.MILLISECONDS), is(true));
    }

    @Test
    public void testInitAll() {
        TestingSource.TestingBuilder builder = TestingSource.builder().init(Config.from(ConfigSources.from(
                CollectionsHelper.mapOf("optional", "true",
                       "polling-strategy.class", TestingPollingStrategy.class.getName(),
                       "retry-policy.class", TestingRetryPolicy.class.getName()
                ))));

        //optional
        assertThat(builder.isMandatory(), is(false));
        //polling-strategy
        assertThat(builder.getPollingStrategy(), is(instanceOf(TestingPollingStrategy.class)));
        //retry-policy
        assertThat(builder.getRetryPolicy(), is(instanceOf(TestingRetryPolicy.class)));
    }

    @Test
    public void testInitNothing() {
        TestingSource.TestingBuilder builder = TestingSource.builder().init(Config.empty());

        //optional
        assertThat(builder.isMandatory(), is(true));
        //polling-strategy
        assertThat(builder.getPollingStrategy(), is(PollingStrategies.nop()));
    }

    private static class TestingSource extends AbstractSource<String, Instant> {

        private final Supplier<Data<String, Instant>> dataSupplier;
        private Runnable fireEventRunnable;

        TestingSource(TestingBuilder builder) {
            super(builder);
            this.dataSupplier = builder.dataSupplier;
            this.fireEventRunnable = builder.fireEventRunnable;
        }

        static TestingBuilder builder() {
            return new TestingSource.TestingBuilder();
        }

        @Override
        protected void fireChangeEvent() {
            fireEventRunnable.run();
        }

        @Override
        protected Optional<Instant> dataStamp() {
            return Optional.of(Instant.MAX);
        }

        @Override
        protected Data<String, Instant> loadData() throws ConfigException {
            return dataSupplier.get();
        }

        private static class TestingBuilder extends Builder<TestingBuilder, Void, TestingSource> {

            private Supplier<Data<String, Instant>> dataSupplier;
            private Runnable fireEventRunnable;

            protected TestingBuilder() {
                super(Void.class);
            }

            TestingBuilder dataSupplier(Supplier<Data<String, Instant>> dataSupplier) {
                this.dataSupplier = dataSupplier;
                return this;
            }

            TestingBuilder fireEventRunnable(Runnable fireEventRunnable) {
                this.fireEventRunnable = fireEventRunnable;
                return this;
            }

            @Override
            public TestingSource build() {
                return new TestingSource(this);
            }
        }
    }

    public static class TestingPollingStrategy implements PollingStrategy {
        @Override
        public Flow.Publisher<PollingEvent> ticks() {
            return Flow.Subscriber::onComplete;
        }
    }

    public static class TestingRetryPolicy implements RetryPolicy {
        @Override
        public <T> T execute(Supplier<T> call) {
            return call.get();
        }
    }

}
