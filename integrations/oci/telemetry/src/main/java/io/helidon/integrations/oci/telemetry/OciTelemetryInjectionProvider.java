/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.telemetry;

import java.util.LinkedList;
import java.util.List;

import io.helidon.integrations.oci.connect.spi.InjectionProvider;

/**
 * Service provider for {@link io.helidon.integrations.oci.connect.spi.InjectionProvider}.
 * @deprecated Do not use directly, this is only used via service loader
 */
@Deprecated
public class OciTelemetryInjectionProvider implements InjectionProvider {
    private static final List<InjectionType<?>> INJECTABLES;

    static {
        List<InjectionType<?>> injectables = new LinkedList<>();

        injectables.add(InjectionType.create(OciMetricsRx.class,
                                             (restApi, config) -> OciMetricsRx.builder()
                                                     .restApi(restApi)
                                                     .config(config)
                                                     .build()));

        injectables.add(InjectionType.create(OciMetrics.class,
                                             (restApi, config) -> OciMetrics.create(OciMetricsRx.builder()
                                                                                            .restApi(restApi)
                                                                                            .config(config)
                                                                                            .build())));

        INJECTABLES = List.copyOf(injectables);
    }

    /**
     * Public constructor must be present for service loader.
     * @deprecated Do not use directly, this is only used via service loader
     */
    @Deprecated
    public OciTelemetryInjectionProvider() {
    }

    @Override
    public List<InjectionType<?>> injectables() {
        return INJECTABLES;
    }
}
