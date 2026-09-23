/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HttpTransportObserver;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverLifecycle;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverProvider;
import io.helidon.webclient.spi.ClientConnectionCache;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(20)
class HttpTransportConnectionCacheTest {
    @Test
    void absentAndDisabledDoNotCreateHandlesOrResolveScopes() {
        var factoryCalls = new AtomicInteger();
        var absent = HttpTransportConnectionCache.create(TestCache.class, HttpClientConfig.create(), () -> {
            factoryCalls.incrementAndGet();
            return new TestCache();
        });
        var provider = new TestProvider(new Object());
        provider.enabled = false;
        var disabled = HttpTransportConnectionCache.create(TestCache.class, config(true, provider), () -> {
            factoryCalls.incrementAndGet();
            return new TestCache();
        });

        assertThat(absent.isEmpty(), is(true));
        assertThat(disabled.isEmpty(), is(true));
        assertThat(factoryCalls.get(), is(0));
        assertThat(provider.scopes.get(), is(0));
        assertThat(provider.creates.get(), is(0));
    }

    @Test
    void unusedHandleDoesNotOwnResources() {
        var provider = new TestProvider(new Object());
        var factoryCalls = new AtomicInteger();
        var handle = HttpTransportConnectionCache.create(TestCache.class, config(true, provider), () -> {
            factoryCalls.incrementAndGet();
            return new TestCache();
        }).orElseThrow();

        handle.closeResource();
        handle.closeResource();

        assertThat(factoryCalls.get(), is(0));
        assertThat(provider.scopes.get(), is(0));
        assertThat(provider.creates.get(), is(0));
        assertThat(provider.stops.get(), is(0));
        assertThat(handle.completion().toCompletableFuture().isDone(), is(true));
        assertThrows(IllegalStateException.class, handle::cache);
        assertThrows(IllegalStateException.class, handle::observer);
    }

    @Test
    void sameScopeSharesCacheAndObserverUntilLastOwnerCloses() {
        Object scope = new Object();
        var firstProvider = new TestProvider(scope);
        var secondProvider = new TestProvider(scope);
        var first = handle(true, firstProvider);
        var second = handle(true, secondProvider);
        TestCache cache = first.cache();
        try {
            assertThat(second.cache(), sameInstance(cache));
            assertThat(second.observer(), sameInstance(first.observer()));
            assertThat(firstProvider.starts.get(), is(1));
            assertThat(secondProvider.starts.get(), is(0));

            first.closeResource();

            assertThat(cache.closes.get(), is(0));
            assertThat(firstProvider.stops.get(), is(0));
            assertThat(first.completion().toCompletableFuture().isDone(), is(false));
            assertThat(second.cache(), sameInstance(cache));
        } finally {
            first.closeResource();
            second.closeResource();
        }
        assertThat(cache.closes.get(), is(1));
        assertThat(firstProvider.stops.get(), is(1));
        assertThat(first.completion().toCompletableFuture().isDone(), is(true));
        assertThat(second.completion().toCompletableFuture().isDone(), is(true));
        assertThrows(IllegalStateException.class, second::cache);
    }

    @Test
    void scopeIdentitySeparatesEqualScopes() {
        var first = handle(true, new TestProvider(new EqualScope(1)));
        var second = handle(true, new TestProvider(new EqualScope(1)));
        try {
            assertThat(second.cache(), not(sameInstance(first.cache())));
            assertThat(second.observer(), not(sameInstance(first.observer())));
        } finally {
            first.closeResource();
            second.closeResource();
        }
    }

    @Test
    void privateCachesDoNotShareEvenWithTheSameProvider() {
        var provider = new TestProvider(new Object());
        var first = handle(false, provider);
        var second = handle(false, provider);
        try {
            assertThat(second.cache(), not(sameInstance(first.cache())));
            assertThat(provider.starts.get(), is(2));
        } finally {
            first.closeResource();
            second.closeResource();
        }
        assertThat(provider.stops.get(), is(2));
    }

    @Test
    void protocolCacheTypesDoNotShare() {
        var provider = new TestProvider(new Object());
        var first = handle(true, provider);
        var second = HttpTransportConnectionCache.create(OtherCache.class,
                                                         config(true, provider),
                                                         OtherCache::new).orElseThrow();
        try {
            assertThat(second.cache(), not(sameInstance(first.cache())));
            assertThat(provider.starts.get(), is(2));
        } finally {
            first.closeResource();
            second.closeResource();
        }
    }

    @Test
    void providerOrderAndDuplicateScopesDoNotSplitTheCache() {
        var firstProvider = new TestProvider(new Object());
        var secondProvider = new TestProvider(new Object());
        var duplicate = new TestProvider(firstProvider.scope);
        var first = handle(true, firstProvider, secondProvider, duplicate);
        var second = handle(true, secondProvider, firstProvider);
        try {
            assertThat(second.cache(), sameInstance(first.cache()));
            assertThat(firstProvider.starts.get(), is(1));
            assertThat(secondProvider.starts.get(), is(1));
            assertThat(duplicate.starts.get(), is(0));
        } finally {
            first.closeResource();
            second.closeResource();
        }
    }

    @Test
    void differentProviderTypesUsingTheSameScopeRemainIndependent() {
        Object scope = new Object();
        var firstProvider = new TestProvider(scope);
        TestProvider secondProvider = new OtherProvider(scope);
        var first = handle(true, firstProvider);
        var second = handle(true, secondProvider);
        var combined = handle(true, firstProvider, secondProvider);
        try {
            assertThat(second.cache(), not(sameInstance(first.cache())));
            combined.observer().connectionOpened(HttpTransportObserver.Role.CLIENT,
                                                 HttpTransportObserver.TRANSPORT_TCP,
                                                 HttpTransportObserver.Handshake.NONE);
            assertThat(firstProvider.opens.get(), is(1));
            assertThat(secondProvider.opens.get(), is(1));
            assertThat(firstProvider.starts.get(), is(2));
            assertThat(secondProvider.starts.get(), is(2));
        } finally {
            first.closeResource();
            second.closeResource();
            combined.closeResource();
        }
    }

    @Test
    void closingDoesNotWaitForActiveObservationCleanup() {
        var provider = new TestProvider(new Object());
        provider.stopCompletion = new CompletableFuture<>();
        var handle = handle(true, provider);
        TestCache cache = handle.cache();

        handle.closeResource();

        assertThat(cache.closes.get(), is(1));
        assertThat(provider.stops.get(), is(1));
        assertThat(handle.completion().toCompletableFuture().isDone(), is(false));
        provider.stopCompletion.complete(null);
        assertThat(handle.completion().toCompletableFuture().isDone(), is(true));
    }

    @Test
    void completionViewCannotCompleteUnderlyingCleanup() {
        var provider = new TestProvider(new Object());
        provider.stopCompletion = new CompletableFuture<>();
        var handle = handle(true, provider);
        handle.cache();
        handle.closeResource();

        handle.completion().toCompletableFuture().complete(null);

        assertThat(handle.completion().toCompletableFuture().isDone(), is(false));
        provider.stopCompletion.complete(null);
        assertThat(handle.completion().toCompletableFuture().isDone(), is(true));
    }

    @Test
    void concurrentAcquisitionInitializesOnePartition() throws Exception {
        var provider = new TestProvider(new Object());
        var factoryEntered = new CountDownLatch(1);
        var finishFactory = new CountDownLatch(1);
        var factoryCalls = new AtomicInteger();
        var first = HttpTransportConnectionCache.create(TestCache.class, config(true, provider), () -> {
            factoryCalls.incrementAndGet();
            factoryEntered.countDown();
            await(finishFactory);
            return new TestCache();
        }).orElseThrow();
        var second = handle(true, provider);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstResult = executor.submit(first::cache);
            try {
                await(factoryEntered);
                var secondResult = executor.submit(second::cache);
                finishFactory.countDown();
                assertThat(secondResult.get(10, TimeUnit.SECONDS), sameInstance(firstResult.get(10, TimeUnit.SECONDS)));
            } finally {
                finishFactory.countDown();
                first.closeResource();
                second.closeResource();
            }
        }
        assertThat(factoryCalls.get(), is(1));
        assertThat(provider.starts.get(), is(1));
        assertThat(provider.stops.get(), is(1));
    }

    @Test
    void newAcquisitionDoesNotWaitForRetiredCacheToClose() throws Exception {
        var provider = new TestProvider(new Object());
        var closeEntered = new CountDownLatch(1);
        var finishClose = new CountDownLatch(1);
        var firstCache = new TestCache(() -> {
            closeEntered.countDown();
            await(finishClose);
        });
        var first = HttpTransportConnectionCache.create(TestCache.class,
                                                        config(true, provider),
                                                        () -> firstCache).orElseThrow();
        var second = handle(true, provider);
        first.cache();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var closing = executor.submit(first::closeResource);
            try {
                await(closeEntered);
                assertThat(second.cache(), not(sameInstance(firstCache)));
                assertThat(provider.starts.get(), is(2));
                finishClose.countDown();
                closing.get(10, TimeUnit.SECONDS);
            } finally {
                finishClose.countDown();
                first.closeResource();
                second.closeResource();
            }
        }
        assertThat(provider.stops.get(), is(2));
    }

    @Test
    void cacheFactoryFailureDoesNotRetainARegistryPartition() {
        var provider = new TestProvider(new Object());
        var failure = new IllegalStateException("Cache factory failed");
        var failed = HttpTransportConnectionCache.create(TestCache.class, config(true, provider), () -> {
            throw failure;
        }).orElseThrow();

        assertThat(assertThrows(IllegalStateException.class, failed::cache), sameInstance(failure));
        assertThat(provider.starts.get(), is(0));
        assertThat(failed.completion().toCompletableFuture().isCompletedExceptionally(), is(true));
        assertThrows(IllegalStateException.class, failed::cache);

        var replacement = handle(true, provider);
        try {
            replacement.cache();
            assertThat(provider.starts.get(), is(1));
        } finally {
            replacement.closeResource();
        }
    }

    @Test
    void failedObserverStartIsRolledBackAndOtherObserversContinue() {
        var failedProvider = new TestProvider(new Object());
        failedProvider.startFailure = new IllegalStateException("Start failed");
        failedProvider.stopCompletion = new CompletableFuture<>();
        var remainingProvider = new TestProvider(new Object());
        var handle = handle(true, failedProvider, remainingProvider);
        try {
            handle.observer().connectionOpened(HttpTransportObserver.Role.CLIENT,
                                               HttpTransportObserver.TRANSPORT_TCP,
                                               HttpTransportObserver.Handshake.NONE);
            assertThat(failedProvider.stops.get(), is(1));
            assertThat(remainingProvider.opens.get(), is(1));
        } finally {
            handle.closeResource();
        }
        assertThat(failedProvider.stops.get(), is(1));
        assertThat(remainingProvider.stops.get(), is(1));
        assertThat(handle.completion().toCompletableFuture().isDone(), is(false));
        failedProvider.stopCompletion.complete(null);
        assertThat(handle.completion().toCompletableFuture().isDone(), is(true));
    }

    @Test
    void cacheCloseFailureStillStopsEveryObserver() {
        var firstProvider = new TestProvider(new Object());
        var secondProvider = new TestProvider(new Object());
        var failure = new IllegalStateException("Cache close failed");
        var handle = HttpTransportConnectionCache.create(TestCache.class, config(true, firstProvider, secondProvider),
                                                         () -> new TestCache(() -> {
                                                             throw failure;
                                                         })).orElseThrow();
        handle.cache();

        handle.closeResource();

        assertThat(firstProvider.stops.get(), is(1));
        assertThat(secondProvider.stops.get(), is(1));
        CompletionException actual = assertThrows(CompletionException.class,
                                                  () -> handle.completion().toCompletableFuture().join());
        assertThat(actual.getCause(), sameInstance(failure));
    }

    @Test
    void stopFailureDoesNotPreventOtherObserversFromStopping() {
        var firstProvider = new TestProvider(new Object());
        firstProvider.stopFailure = new IllegalStateException("Stop failed");
        var secondProvider = new TestProvider(new Object());
        var handle = handle(true, firstProvider, secondProvider);
        handle.cache();

        handle.closeResource();

        assertThat(firstProvider.stops.get(), is(1));
        assertThat(secondProvider.stops.get(), is(1));
        assertThat(handle.completion().toCompletableFuture().isCompletedExceptionally(), is(true));
    }

    private static HttpTransportConnectionCache<TestCache> handle(boolean shared, TestProvider... providers) {
        return HttpTransportConnectionCache.create(TestCache.class, config(shared, providers), TestCache::new).orElseThrow();
    }

    private static HttpClientConfig config(boolean shared, TestProvider... providers) {
        var builder = HttpClientConfig.builder().shareConnectionCache(shared);
        for (TestProvider provider : providers) {
            builder.addService(provider);
        }
        return builder.build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for a test lifecycle transition");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for a test lifecycle transition", e);
        }
    }

    private record EqualScope(int value) {
    }

    private static class TestCache extends ClientConnectionCache {
        private final AtomicInteger closes = new AtomicInteger();
        private final Runnable onClose;

        private TestCache() {
            this(() -> {
            });
        }

        private TestCache(Runnable onClose) {
            super(false);
            this.onClose = onClose;
        }

        @Override
        public void closeResource() {
            closes.incrementAndGet();
            onClose.run();
        }

        @Override
        protected void evict() {
        }
    }

    private static final class OtherCache extends TestCache {
    }

    private static class TestProvider implements WebClientService, ObserverProvider {
        private final Object scope;
        private final AtomicInteger scopes = new AtomicInteger();
        private final AtomicInteger creates = new AtomicInteger();
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger stops = new AtomicInteger();
        private final AtomicInteger opens = new AtomicInteger();

        private boolean enabled = true;
        private RuntimeException startFailure;
        private RuntimeException stopFailure;
        private CompletableFuture<Void> stopCompletion = CompletableFuture.completedFuture(null);

        private TestProvider(Object scope) {
            this.scope = scope;
        }

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            return chain.proceed(request);
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Object scope() {
            scopes.incrementAndGet();
            return scope;
        }

        @Override
        public ObserverLifecycle createObserver() {
            creates.incrementAndGet();
            return new ObserverLifecycle() {
                @Override
                public HttpTransportObserver start() {
                    starts.incrementAndGet();
                    if (startFailure != null) {
                        throw startFailure;
                    }
                    return (_, _, _) -> {
                        opens.incrementAndGet();
                        return HttpTransportObserver.ConnectionObservation.noop();
                    };
                }

                @Override
                public CompletionStage<Void> stop() {
                    stops.incrementAndGet();
                    if (stopFailure != null) {
                        throw stopFailure;
                    }
                    return stopCompletion;
                }
            };
        }
    }

    private static final class OtherProvider extends TestProvider {
        private OtherProvider(Object scope) {
            super(scope);
        }
    }
}
