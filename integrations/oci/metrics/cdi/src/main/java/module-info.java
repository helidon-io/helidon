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

/**
 * Integrating with OCI Metrics Using CDI.
 */
module io.helidon.integrations.oci.metrics.cdi {
    requires io.helidon.config.mp;
    requires io.helidon.integrations.oci.metrics;
    requires io.helidon.microprofile.config;
    requires io.helidon.microprofile.server;

    requires oci.java.sdk.monitoring;

    requires jakarta.interceptor;
    provides jakarta.enterprise.inject.spi.Extension with io.helidon.integrations.oci.metrics.cdi.OciMetricsCdiExtension;

    opens io.helidon.integrations.oci.metrics.cdi to weld.core.impl, io.helidon.microprofile.cdi;

    exports io.helidon.integrations.oci.metrics.cdi;
}
