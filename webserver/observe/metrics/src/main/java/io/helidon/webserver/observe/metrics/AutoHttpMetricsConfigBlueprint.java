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

package io.helidon.webserver.observe.metrics;

import java.util.List;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Automatic metrics collection settings.
 */
@Prototype.Blueprint(decorator = AutoHttpMetricsConfigSupport.BuilderDecorator.class)
@Prototype.Configured
@Prototype.CustomMethods(AutoHttpMetricsConfigSupport.CustomMethods.class)
interface AutoHttpMetricsConfigBlueprint {

    /**
     * Whether automatic metrics collection as a whole is enabled.
     *
     * @return automatic metrics collection enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Socket names for sockets to be instrumented with automatic metrics. Defaults to all sockets.
     *
     * @return socket names
     */
    @Option.Configured
    Set<String> sockets();

    /**
     * Automatic metrics collection settings. Default excludes built-in Helidon paths (e.g., metrics, health).
     * A request's path and HTTP method are checked against each entry under {@code paths} in order.
     * <ul>
     *     <li>If a request matches no entry, then the request is measured.</li>
     *     <li>If a request matches multiple entries, then the first match wins.</li>
     * </ul>
     *
     * @return automatic metrics collection settings
     */
    @Option.Configured
    @Option.Singular
    List<AutoHttpMetricsPathConfig> paths();

    /**
     * Elective attribute for which to opt in. Each string in the list is of the form
     * {@code meter-name:attribute-name} where {@code meter-name} is the name of the meter and {@code attribute-name} is the
     * name of an attribute (tag) which is optional on that meter.
     *
     * @return opt-in attributes to be provided
     */
    @Option.Configured
    List<String> optIn();

}
