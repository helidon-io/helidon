/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.tracing;

import io.helidon.common.config.Config;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.observe.spi.ObserveProvider;
import io.helidon.webserver.observe.spi.Observer;

/**
 * {@link java.util.ServiceLoader} provider implementation for tracing observe provider.
 *
 * @deprecated this type is only to be used from {@link java.util.ServiceLoader}
 */
@Deprecated
public class TracingObserveProvider implements ObserveProvider {
    /**
     * Default constructor required by {@link java.util.ServiceLoader}. Do not use.
     *
     * @deprecated this constructor must be public for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public TracingObserveProvider() {
    }

    @Override
    public String configKey() {
        return "tracing";
    }

    @Override
    public Observer create(Config config, String name) {
        Config tracingConfig = config.root().get("tracing");
        Tracer tracer = Contexts.globalContext()
                .get(Tracer.class)
                .orElseGet(() -> {
                    if (tracingConfig.exists()) {
                        return TracerBuilder.create(tracingConfig)
                                .build();
                    }
                    return Tracer.global();
                });

        return TracingObserverConfig.builder()
                .tracer(tracer)
                .config(tracingConfig) // read from root `tracing`
                .config(config) // update with server.features.observe.tracing
                .name(name)
                .build();
    }
}
