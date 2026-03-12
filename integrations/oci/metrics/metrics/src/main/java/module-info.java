/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Integrating with OCI Metrics.
 */
@Features.Preview
@Features.Name("OCI Metrics")
@Features.Description("OCI Metrics Export")
@Features.Flavor({HelidonFlavor.MP, HelidonFlavor.SE})
@Features.Path({"OCI", "Metrics"})
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.integrations.oci.metrics {

    requires io.helidon.common.resumable;
    requires io.helidon.http;

    requires oci.java.sdk.common;

    requires static io.helidon.config.metadata;

    requires transitive io.helidon.common;
    requires transitive io.helidon.config;
    requires transitive io.helidon.metrics.api;
    requires transitive io.helidon.webserver;
    requires transitive oci.java.sdk.monitoring;
    requires io.helidon.integrations.oci;
    requires io.helidon;
    requires io.helidon.common.features.api;

    exports io.helidon.integrations.oci.metrics;

    provides io.helidon.metrics.spi.MetricsPublisherProvider with io.helidon.integrations.oci.metrics.OciMetricsPublisherProvider;

 }
