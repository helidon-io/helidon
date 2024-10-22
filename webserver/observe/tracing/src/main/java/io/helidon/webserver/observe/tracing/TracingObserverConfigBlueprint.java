/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.config.TracingConfig;
import io.helidon.webserver.observe.ObserverConfigBase;
import io.helidon.webserver.observe.spi.ObserveProvider;

/**
 * Configuration of Tracing observer.
 *
 * @see io.helidon.webserver.observe.tracing.TracingObserver#create(io.helidon.webserver.observe.tracing.TracingObserverConfig)
 * @see io.helidon.webserver.observe.tracing.TracingObserver#builder()
 */
@Prototype.Blueprint(decorator = TracingObserverSupport.TracingObserverDecorator.class)
@Prototype.Configured(root = false, value = "tracing")
@Prototype.Provides(ObserveProvider.class)
interface TracingObserverConfigBlueprint extends ObserverConfigBase, Prototype.Factory<TracingObserver> {
    @Option.Default("tracing")
    @Override
    String name();

    /**
     * Use the provided configuration as a default for any request.
     *
     * @return default web server tracing configuration
     */
    @Option.Configured(merge = true)
    @Option.DefaultCode("TracingConfig.ENABLED")
    TracingConfig envConfig();

    /*
     * OpenTracing spec states that certain MP paths need to be disabled by default.
     * Note that if a user changes the default location of any of these using
     * web-context's, then they would need to provide these exclusions manually.
     *
     * The default path configs below are overridable via configuration. For example,
     * health could be enabled by setting {@code tracing.paths.0.path=/observe/health} and
     * {@code tracing.paths.0.enabled=true}.
     *
     * By default we disable both the SE-style paths ({@code /observe/health}) and the MP-style paths ({@code /health}).
     */

    /**
     * Path specific configuration of tracing.
     *
     * @return configuration of tracing for specific paths
     */
    @Option.Configured("paths")
    @Option.Singular
    @Option.DefaultCode("new @java.util.ArrayList@(@java.util.List@.of(PathTracingConfig.builder()\n"
            + "                                  .path(\"/metrics/*\")\n"
            + "                                  .tracingConfig(TracingConfig.DISABLED)\n"
            + "                                  .build(), \n"
            + "                                  PathTracingConfig.builder()\n"
            + "                                  .path(\"/observe/metrics/*\")\n"
            + "                                  .tracingConfig(TracingConfig.DISABLED)\n"
            + "                                  .build(), \n"
            + "                                  PathTracingConfig.builder()\n"
            + "                                  .path(\"/health/*\")\n"
            + "                                  .tracingConfig(TracingConfig.DISABLED)\n"
            + "                                  .build(), \n"
            + "                                  PathTracingConfig.builder()\n"
            + "                                  .path(\"/observe/health/*\")\n"
            + "                                  .tracingConfig(TracingConfig.DISABLED)\n"
            + "                                  .build(), \n"
            + "                                  PathTracingConfig.builder()\n"
            + "                                  .path(\"/openapi/*\")\n"
            + "                                  .tracingConfig(TracingConfig.DISABLED)\n"
            + "                                  .build(), \n"
            + "                                  PathTracingConfig.builder()\n"
            + "                                  .path(\"/observe/openapi/*\")\n"
            + "                                  .tracingConfig(TracingConfig.DISABLED)\n"
            + "                                  .build()))")
    List<PathTracingConfig> pathConfigs();

    /**
     * Tracer to use to extract inbound span context.
     *
     * @return tracer to use
     */
    @Option.Required
    Tracer tracer();

    /**
     * Weight of the feature registered with WebServer.
     * Changing weight may cause tracing to be executed at a different time (such as after security, or even after
     * all routes). Please understand feature weights before changing this order.
     *
     * @return weight of tracing feature
     */
    @Option.Configured
    @Option.DefaultDouble(900)
    double weight();

    /**
     * Sockets to trace.
     * <p>
     * If empty, all sockets will be traced. The default socket without any tag, additional sockets with a tag with the
     * socket name.
     *
     * @return set of sockets to trace
     */
    @Option.Singular
    Set<String> sockets();
}
