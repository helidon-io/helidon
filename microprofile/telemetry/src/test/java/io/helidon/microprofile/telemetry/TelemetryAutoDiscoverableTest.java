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
package io.helidon.microprofile.telemetry;

import io.helidon.microprofile.telemetry.spi.HelidonTelemetryClientFilterHelper;
import io.helidon.tracing.Tracer;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.core.FeatureContext;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemetryAutoDiscoverableTest {

    @Test
    void skipsClientFilterWhenCdiIsUnavailable() {
        FeatureContext context = mock(FeatureContext.class);

        new TelemetryAutoDiscoverable(() -> {
            throw new IllegalStateException("No CDI provider");
        }).configure(context);

        verify(context).register(HelidonTelemetryContainerFilter.class);
        verify(context, never()).register(any(HelidonTelemetryClientFilter.class));
    }

    @Test
    void skipsClientFilterWhenTracerIsNotResolvable() {
        FeatureContext context = mock(FeatureContext.class);
        CDI<Object> cdi = mock(CDI.class);
        Instance<Tracer> tracers = mock(Instance.class);
        when(cdi.select(Tracer.class)).thenReturn(tracers);
        when(tracers.isResolvable()).thenReturn(false);

        new TelemetryAutoDiscoverable(() -> cdi).configure(context);

        verify(context).register(HelidonTelemetryContainerFilter.class);
        verify(context, never()).register(any(HelidonTelemetryClientFilter.class));
    }

    @Test
    void registersCdiConstructedClientFilterWhenTracerIsResolvable() {
        FeatureContext context = mock(FeatureContext.class);
        CDI<Object> cdi = mock(CDI.class);
        Instance<Tracer> tracers = mock(Instance.class);
        Instance<HelidonTelemetryClientFilterHelper> helpers = mock(Instance.class);
        Tracer tracer = mock(Tracer.class);
        when(cdi.select(Tracer.class)).thenReturn(tracers);
        when(tracers.isResolvable()).thenReturn(true);
        when(tracers.get()).thenReturn(tracer);
        when(cdi.select(HelidonTelemetryClientFilterHelper.class)).thenReturn(helpers);

        new TelemetryAutoDiscoverable(() -> cdi).configure(context);

        verify(context).register(HelidonTelemetryContainerFilter.class);
        verify(context).register(any(HelidonTelemetryClientFilter.class));
    }
}
