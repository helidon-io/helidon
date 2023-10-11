/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tracing;

import java.lang.System.Logger.Level;
import java.util.List;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.JaxRsApplication;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.config.TracingConfig;
import io.helidon.webserver.observe.tracing.TracingObserver;

import io.opentelemetry.opentracingshim.OpenTracingShim;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Extension;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * CDI extension for Microprofile Tracing implementation.
 */
public class TracingCdiExtension implements Extension {
    private static final System.Logger LOGGER = System.getLogger(TracingCdiExtension.class.getName());

    private io.helidon.tracing.Tracer tracer;
    private Config config;

    /**
     * Add our beans to CDI, so we do not need to use {@code beans.xml}.
     *
     * @param bbd CDI event
     */
    private void observeBeforeBeanDiscovery(@Observes BeforeBeanDiscovery bbd) {
        bbd.addAnnotatedType(MpTracingInterceptor.class, "TracingInterceptor");
        bbd.addAnnotatedType(TracerProducer.class, "TracingTracerProducer");
    }

    // must be higher priority than security, so tracer is ready when IDCS and OIDC uses webclient
    // the client is used in security configuration
    private void setupTracer(@Observes @Priority(PLATFORM_BEFORE) @RuntimeStart Config rootConfig,
                             BeanManager bm) {

        String serviceName = bm.getExtension(JaxRsCdiExtension.class).serviceName();
        config = rootConfig.get("tracing");
        tracer = TracerBuilder.create(serviceName)
                .config(config)
                .build();
        TracingConfig tracingConfig = TracingConfig.create(config);
        Contexts.globalContext().register(tracingConfig);

        if (!tracer.enabled()) {
            LOGGER.log(Level.WARNING, "helidon-microprofile-tracing is on the classpath, yet there is no tracer "
                                   + "implementation library or tracing is explicitly disabled. Tracing uses a no-op tracer. "
                                   + "As a result, no tracing will be configured for WebServer and JAX-RS");

            Contexts.globalContext().register(io.helidon.tracing.Tracer.noOp());
            Contexts.globalContext().register(GlobalTracer.get());
            // no need to register all of this
            return;
        }

        Tracer registeredTracer;
        try {
            registeredTracer = tracer.unwrap(Tracer.class);
        } catch (Exception e) {
            try {
                io.opentelemetry.api.trace.Tracer otelTracer = tracer.unwrap(io.opentelemetry.api.trace.Tracer.class);
                registeredTracer = OpenTracingShim.createTracerShim(otelTracer);
            } catch (Exception ex) {
                throw new DeploymentException("MicroProfile tracing requires an OpenTracing or OpenTelemetry based tracer", ex);
            }
        }

        Tracer openTracingTracer = registeredTracer;

        // tracer is available in global
        Contexts.globalContext().register(openTracingTracer);
        Contexts.globalContext().register(this.tracer);

        Contexts.context()
                .ifPresent(ctx -> ctx.register(openTracingTracer));
    }

    private void serverTracer(@Observes @Priority(PLATFORM_BEFORE + 1) @Initialized(ApplicationScoped.class) Object event,
                              BeanManager bm) {
        if (!tracer.enabled()) {
            return;
        }

        ServerCdiExtension server = bm.getExtension(ServerCdiExtension.class);
        JaxRsCdiExtension jaxrs = bm.getExtension(JaxRsCdiExtension.class);

        server.addObserver(TracingObserver.create(tracer, config));

        // we need that `tracing.service` is a required configuration, yet we do not want to just fail
        // if not present. Let's make a "guess" about the service name
        List<JaxRsApplication> jaxRsApps = jaxrs.applicationsToRun();

        jaxRsApps
                .forEach(app -> app.resourceConfig().register(MpTracingFilter.class));
    }
}
