/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.observe.health;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.nima.observe.spi.ObserveProvider;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * {@link java.util.ServiceLoader} provider implementation for health observe provider.
 */
public class HealthObserveProvider implements ObserveProvider {
    private final HealthFeature explicitService;

    /**
     * Default constructor required by {@link java.util.ServiceLoader}. Do not use.
     *
     * @deprecated use {@link #create(HealthFeature)} or
     *         {@link #create()} instead.
     */
    @Deprecated
    public HealthObserveProvider() {
        this(null);
    }

    private HealthObserveProvider(HealthFeature explicitService) {
        this.explicitService = explicitService;
    }

    /**
     * Create a new instance with health checks discovered through {@link java.util.ServiceLoader}.
     *
     * @return a new provider
     */
    public static ObserveProvider create() {
        return create(HealthFeature.create());
    }

    /**
     * Create using a configured observer.
     * In this case configuration provided by the {@link io.helidon.nima.observe.ObserveFeature} is ignored except for
     * the reserved option {@code endpoint}).
     *
     * @param service service to use
     * @return a new provider based on the observer
     */
    public static ObserveProvider create(HealthFeature service) {
        return new HealthObserveProvider(service);
    }

    @Override
    public String configKey() {
        return "health";
    }

    @Override
    public String defaultEndpoint() {
        return explicitService == null ? "health" : explicitService.configuredContext();
    }

    @Override
    public void register(Config config, String componentPath, HttpRouting.Builder routing) {
        HealthFeature observer = explicitService == null
                ? HealthFeature.builder().webContext(componentPath).config(config).build()
                : explicitService;

        if (observer.enabled()) {
            // when created as part of observer, we need to use component path
            observer.context(componentPath);
            routing.addFeature(observer);
        } else {
            routing.get(componentPath + "/*", (req, res) -> res.status(Http.Status.SERVICE_UNAVAILABLE_503)
                    .send());
        }
    }
}
