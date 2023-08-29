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

package io.helidon.integrations.oci.tls.certificates;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class TestingMetricsHelper implements MetricsTracker {

    final ConcurrentHashMap<String, Metric> map = new ConcurrentHashMap<>();
    private final LifecycleHook lifecycleHook;

    @Inject
    TestingMetricsHelper(Optional<LifecycleHook> lifecycleHook) {
        this.lifecycleHook = lifecycleHook.orElseThrow();
    }

    @Override
    public Gauge getOrCreateGauge(String name,
                                  String description) {
        lifecycleHook.registerShutdownConsumer((ignoredEvent) -> reset());
        return (Gauge) map.computeIfAbsent(name, (n) -> new Gauge(n, description));
    }

    void reset() {
        map.clear();
    }

    int metrics() {
        return map.size();
    }

    @SuppressWarnings("unchecked")
    <T extends Metric> T metric(String name) {
        return Objects.requireNonNull((T) map.get(name));
    }


    static class Gauge implements MetricsTracker.Gauge {
        private final String name;
        private final String description;
        private long count;
        private long sum;

        Gauge(String name,
              String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public void update(long val) {
            sum += val;
            count++;
            synchronized (this) {
                notifyAll();
            }
        }

        @Override
        public long sum() {
            return sum;
        }
    }

}
