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

package io.helidon.integrations.oci.metrics.otherpkg;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.resumable.Resumable;
import io.helidon.metrics.api.MetricsPublisher;
import io.helidon.spi.HelidonShutdownHandler;

class DelegatingOciMetricsService implements MetricsPublisher,
                                             Resumable,
                                             HelidonShutdownHandler,
                                             AutoCloseable,
                                             RuntimeType.Api<DelegatingOciMetricsConfig> {

    private final DelegatingOciMetricsConfig prototype;

    static DelegatingOciMetricsService create(DelegatingOciMetricsConfig metricsConfig) {
        return new DelegatingOciMetricsService(metricsConfig);
    }

    static DelegatingOciMetricsConfig.Builder builder() {
        return DelegatingOciMetricsConfig.builder();
    }

    static DelegatingOciMetricsService create(Consumer<DelegatingOciMetricsConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    private DelegatingOciMetricsService(DelegatingOciMetricsConfig prototype) {
        this.prototype = prototype;
        /*
        Building the delegate (not just building the prototype) causes the OciMetricsService to be
        created and started.
         */
        prototype.delegate().build();
    }

    @Override
    public DelegatingOciMetricsConfig prototype() {
        return prototype;
    }

    @Override
    public boolean enabled() {
        return prototype().enabled();
    }

    @Override
    public String name() {
        return prototype().name().orElseGet(() -> "delegating-oci");
    }

    @Override
    public String type() {
        return "oci";
    }

    @Override
    public void suspend() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void close() throws Exception {

    }
}
