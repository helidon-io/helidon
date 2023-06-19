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
package io.helidon.metrics.microprofile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.MetricRegistry;

public class MpRegistryFactory {

    public static final String APPLICATION_SCOPE = "application";
    public static final String BASE_SCOPE = "base";
    public static final String VENDOR_SCOPE = "vendor";

    private static MpRegistryFactory INSTANCE;

    private static final ReentrantLock lock = new ReentrantLock();
    private final Exception creation;

    private Config mpConfig;
    private final Map<String, MpMetricRegistry> registries = new HashMap<>();
    private final PrometheusMeterRegistry prometheusMeterRegistry;


    public static MpRegistryFactory create(Config mpConfig) {
        lock.lock();
        try {
            if (INSTANCE == null) {
                INSTANCE = new MpRegistryFactory(mpConfig);
                return INSTANCE;
            } else {
                throw new IllegalStateException("Attempt to set up MpRegistryFactory multiple times; previous invocation follows: ",
                                                INSTANCE.creation);
            }
        } finally {
            lock.unlock();
        }
    }

    public static MpRegistryFactory get() {
        lock.lock();
        try {
            if (INSTANCE == null) {
                INSTANCE = new MpRegistryFactory(ConfigProvider.getConfig());
            }
            return INSTANCE;
        } finally {
            lock.unlock();
        }
    }

    private MpRegistryFactory(Config mpConfig) {
        creation = new Exception("Initial creation of " + MpRegistryFactory.class.getSimpleName());
        this.mpConfig = mpConfig;
        prometheusMeterRegistry = findOrAddPrometheusRegistry();
        registries.put(APPLICATION_SCOPE, MpMetricRegistry.create(APPLICATION_SCOPE, Metrics.globalRegistry));
        registries.put(BASE_SCOPE, BaseRegistry.create(Metrics.globalRegistry));
        registries.put(VENDOR_SCOPE, MpMetricRegistry.create(VENDOR_SCOPE, Metrics.globalRegistry));
    }

    public MetricRegistry registry(String scope) {
//        if (INSTANCE == null) {
//            throw new IllegalStateException("Attempt to use " + MpRegistryFactory.class.getName() + " before invoking create");
//        }
        return registries.computeIfAbsent(scope,
                                                   s -> MpMetricRegistry.create(s, Metrics.globalRegistry));
    }

    public PrometheusMeterRegistry prometheusMeterRegistry() {
//        if (INSTANCE == null) {
//            throw new IllegalStateException("Attempt to use " + MpRegistryFactory.class.getName() + " before invoking create");
//        }
        return prometheusMeterRegistry;
    }

    private PrometheusMeterRegistry findOrAddPrometheusRegistry() {
//        if (INSTANCE == null) {
//            throw new IllegalStateException("Attempt to use " + MpRegistryFactory.class.getName() + " before invoking create");
//        }
        return Metrics.globalRegistry.getRegistries().stream()
                .filter(PrometheusMeterRegistry.class::isInstance)
                .map(PrometheusMeterRegistry.class::cast)
                .findFirst()
                .orElseGet(() -> {
                    PrometheusMeterRegistry result = new PrometheusMeterRegistry(
                            s -> mpConfig.getOptionalValue("mp.metrics." + s, String.class)
                                    .orElse(null));
                    Metrics.addRegistry(result);
                    return result;
                });
    }
}
