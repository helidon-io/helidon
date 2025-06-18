/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.restclientmetrics;

import java.util.HashSet;
import java.util.Set;

import io.helidon.common.LazyValue;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.Priorities;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.spi.RestClientListener;

/**
 * REST client metrics listener to add our filter for outbound REST clients.
 */
public class RestClientMetricsClientListener implements RestClientListener {

    /*
    The listener can be instantiated multiple times, so we delegate the real work to a singleton.
    The listener is an instance field instead of static so tests that create multiple containers in the
    same JVM do not incorrectly all refer to only the first REST client metrics CDI extension.
     */
    private final LazyValue<Listener> listener = LazyValue.create(Listener::new);

    /**
     * For service discovery.
     */
    public RestClientMetricsClientListener() {
    }

    @Override
    public void onNewClient(Class<?> serviceInterface, RestClientBuilder builder) {
        listener.get().onNewClient(serviceInterface, builder);
    }

    private static class Listener {

        private final RestClientMetricsFilter restClientMetricsFilter;

        private final LazyValue<RestClientMetricsConfig> restClientMetricsConfig =
                LazyValue.create(() -> {
                    boolean enabled = ConfigProvider.getConfig()
                            .getOptionalValue(RestClientMetricsFilter.REST_CLIENT_METRICS_CONFIG_KEY
                                                      + ".enabled", Boolean.class)
                            .orElse(true);
                    return RestClientMetricsConfig.builder()
                            .enabled(enabled)
                            .build();
                });

        /*
        In some cases, the system will invoke REST client listeners before CDI has completed its startup. The REST client metrics
         CDI extension registers the meters to be updated for each REST client method, so this listener has nothing to do if
         it runs before CDI has initialized the extension. That said, it needs to keep retrying to locate the CDI extension
         because the extension might (should) become available later. The code below uses a utility method to access this
         lazy value to allow that retry behavior.
         */
        private final LazyValue<RestClientMetricsCdiExtension> ext =
                LazyValue.create(() -> CDI.current().getBeanManager().getExtension(RestClientMetricsCdiExtension.class));

        private final Set<Class<?>> restClientsDiscovered = new HashSet<>();

        private Listener() {
            restClientMetricsFilter = RestClientMetricsFilter.create();
        }

        private void onNewClient(Class<?> serviceInterface, RestClientBuilder builder) {
            if (restClientMetricsConfig.get().enabled()) {
                // Users might build multiple REST client builders (and instances) for a given interface, but we
                // register metrics (and create metric-related work for the filter to do) only upon first
                // discovering a given service interface.
                if (restClientsDiscovered.add(serviceInterface)) {
                    Utils.optOf(ext).ifPresent(ext -> ext.registerMetricsForRestClient(serviceInterface));
                }
                builder.register(restClientMetricsFilter, Priorities.USER - 100);
            }
        }
    }
}
