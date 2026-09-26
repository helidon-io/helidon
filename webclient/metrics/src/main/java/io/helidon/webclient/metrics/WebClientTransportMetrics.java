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

package io.helidon.webclient.metrics;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.metrics.HttpTransportMetrics;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverLifecycle;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverProvider;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

/**
 * HTTP transport metrics for WebClient connections, handshakes, and exchanges.
 *
 * <p>Add this service to a client or configure {@code services.http-metrics.enabled=true}. The service records the
 * {@code helidon.http.*} transport meters with {@code role=client}. These meters can share a registry with WebServer
 * transport metrics, which use {@code role=server}.
 *
 * <p>The client owns the metrics lifecycle. Applications supplying a meter registry must keep it available until
 * the client has closed its connections and completed shutdown.
 */
public final class WebClientTransportMetrics implements WebClientService,
                                                        ObserverProvider,
                                                        RuntimeType.Api<WebClientTransportMetricsConfig> {
    private final WebClientTransportMetricsConfig config;
    private final LazyValue<MeterRegistry> meterRegistry;

    private WebClientTransportMetrics(WebClientTransportMetricsConfig config, Supplier<MeterRegistry> defaultRegistry) {
        this.config = Objects.requireNonNull(config, "config");
        Objects.requireNonNull(defaultRegistry, "defaultRegistry");
        this.meterRegistry = LazyValue.create(() -> Objects.requireNonNull(config.meterRegistry().orElseGet(defaultRegistry),
                                                                         "meterRegistry"));
    }

    /**
     * Creates a builder for client transport metrics.
     *
     * @return a new builder
     */
    public static WebClientTransportMetricsConfig.Builder builder() {
        return WebClientTransportMetricsConfig.builder();
    }

    /**
     * Creates an enabled service using the global meter registry.
     *
     * @return a new service
     */
    public static WebClientTransportMetrics create() {
        return builder().build();
    }

    /**
     * Creates a service from configuration.
     *
     * @param config service configuration
     * @return a new service
     */
    public static WebClientTransportMetrics create(Config config) {
        return builder().config(Objects.requireNonNull(config, "config")).build();
    }

    /**
     * Creates a service from its configuration prototype.
     *
     * @param config service configuration
     * @return a new service
     */
    public static WebClientTransportMetrics create(WebClientTransportMetricsConfig config) {
        return new WebClientTransportMetrics(config, () -> Services.get(MeterRegistry.class));
    }

    /**
     * Creates a service with customized configuration.
     *
     * @param consumer configuration customizer
     * @return a new service
     */
    public static WebClientTransportMetrics create(Consumer<WebClientTransportMetricsConfig.Builder> consumer) {
        return builder().update(Objects.requireNonNull(consumer, "consumer")).build();
    }

    static WebClientTransportMetrics create(WebClientTransportMetricsConfig config, Supplier<MeterRegistry> meterRegistry) {
        return new WebClientTransportMetrics(config, meterRegistry);
    }

    @Override
    public WebClientTransportMetricsConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return "http-metrics";
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public Object scope() {
        return enabled() ? meterRegistry.get() : this;
    }

    @Override
    public ObserverLifecycle createObserver() {
        return new MetricsLifecycle(meterRegistry, enabled());
    }

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
        return Objects.requireNonNull(chain, "chain").proceed(Objects.requireNonNull(request, "request"));
    }

    private static final class MetricsLifecycle implements ObserverLifecycle {
        private final ReentrantLock lock = new ReentrantLock();
        private final LazyValue<MeterRegistry> meterRegistry;
        private final boolean enabled;
        private HttpTransportMetrics.Lease lease;
        private CompletionStage<Void> completion = CompletableFuture.completedStage(null);
        private boolean stopped;

        private MetricsLifecycle(LazyValue<MeterRegistry> meterRegistry, boolean enabled) {
            this.meterRegistry = meterRegistry;
            this.enabled = enabled;
        }

        @Override
        public HttpTransportObserver start() {
            lock.lock();
            try {
                if (!enabled || stopped) {
                    return HttpTransportObserver.noop();
                }
                if (lease == null) {
                    lease = HttpTransportMetrics.acquire(meterRegistry.get());
                }
                return lease;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public CompletionStage<Void> stop() {
            lock.lock();
            try {
                if (!stopped) {
                    stopped = true;
                    if (lease != null) {
                        try {
                            lease.close();
                            completion = lease.completion();
                        } catch (RuntimeException failure) {
                            completion = CompletableFuture.failedFuture(failure);
                        }
                    }
                }
                return completion;
            } finally {
                lock.unlock();
            }
        }
    }
}
