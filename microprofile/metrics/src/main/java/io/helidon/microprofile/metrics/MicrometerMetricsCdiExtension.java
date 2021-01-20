/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.metrics;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.logging.Logger;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.metrics.MicrometerSupport;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.Routing;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Meter;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;

import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * CDI extension for Micrometer metrics.
 */
class MicrometerMetricsCdiExtension extends MetricsCdiExtensionBase {

//    private static final List<Class<? extends Annotation>> METRIC_ANNOTATIONS = List.of(Counted.class, Timed.class);
    private static final Logger LOGGER = Logger.getLogger(MicrometerMetricsCdiExtension.class.getName());

    static class Lazy {
        static MicrometerMetricsCdiExtension INSTANCE = new MicrometerMetricsCdiExtension();
    }

    MicrometerMetricsCdiExtension() {
        super(LOGGER /*, METRIC_ANNOTATIONS */);
    }

    public void registerMeters(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object adv,
            BeanManager bm) {

        Config config = ((Config) ConfigProvider.getConfig()).get("metrics.micrometer");
        MicrometerSupport micrometerSupport = MicrometerSupport.create(config);
        ServerCdiExtension server = bm.getExtension(ServerCdiExtension.class);
        Routing.Builder endpointRouting = MetricsCdiExtensionBase.configuredRoutingBuilder(server, config);
        micrometerSupport.configureEndpoint(endpointRouting);
        Contexts.globalContext().register(MicrometerMeterRegistryFactory.getInstance());
    }

    void observe(@Observes @WithAnnotations({Counted.class, Timed.class}) ProcessAnnotatedType<?> pat) {

    }
}
