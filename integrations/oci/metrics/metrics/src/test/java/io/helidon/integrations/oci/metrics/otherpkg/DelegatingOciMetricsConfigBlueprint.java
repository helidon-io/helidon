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

package io.helidon.integrations.oci.metrics.otherpkg;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.integrations.oci.metrics.OciMetricsConfig;
import io.helidon.integrations.oci.metrics.OciMetricsConfigBase;
import io.helidon.metrics.api.MetricsPublisherConfig;

/**
 * OCI metrics config blueprint with synonyms for certain settings.
 */
@Prototype.Blueprint(decorator = DelegatingOciMetricsConfigSupport.BuilderDecorator.class)
@Prototype.Configured
interface DelegatingOciMetricsConfigBlueprint extends OciMetricsConfigBase,
                                                      MetricsPublisherConfig,
                                                      Prototype.Factory<DelegatingOciMetricsService> {

    /**
     * Fleet to associate with each meter data transmission.
     *
     * @return fleet
     */
    @Option.Configured
    Optional<String> fleet();

    /**
     * Project to associate with each meter data transmission.
     *
     * @return project
     */
    @Option.Configured
    Optional<String> project();

    /**
     * Delegate configuration which actually holds the configuration settings.
     *
     * @return delegate config
     */
    @Option.Access("")
    OciMetricsConfig delegate();
}
