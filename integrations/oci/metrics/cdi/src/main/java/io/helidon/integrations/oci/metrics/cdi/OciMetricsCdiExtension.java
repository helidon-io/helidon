/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.metrics.cdi;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

/**
 * OCI metrics integration CDI extension.
 */
public class OciMetricsCdiExtension implements Extension {

    /**
     * For CDI use only.
     */
    @Deprecated
    public OciMetricsCdiExtension() {
    }

    // A new bean is added to handle the Observer Method as injection does not work here
    void addOciMetricsBean(@Observes BeforeBeanDiscovery event) {
        event.addAnnotatedType(OciMetricsBean.class, OciMetricsBean.class.getName());
    }
}
