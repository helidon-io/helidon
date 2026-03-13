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

package io.helidon.integrations.oci.metrics;

import java.util.function.Supplier;

import io.helidon.integrations.oci.OciConfig;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.MonitoringClient;

/**
 * Factory for a instance of {@link com.oracle.bmc.monitoring.Monitoring} configured using
 * {@link io.helidon.integrations.oci.OciConfig} and {@link com.oracle.bmc.auth.BasicAuthenticationDetailsProvider},
 * both retrieved from the service registry.
 */
@Service.Singleton
class MonitoringFactory implements Supplier<Monitoring> {

    private final OciConfig ociConfig;
    private final BasicAuthenticationDetailsProvider basicAuthenticationDetailsProvider;

    @Service.Inject
    MonitoringFactory(OciConfig ociConfig, BasicAuthenticationDetailsProvider basicAuthenticationDetailsProvider) {
        this.ociConfig = ociConfig;
        this.basicAuthenticationDetailsProvider = basicAuthenticationDetailsProvider;
    }

    @Override
    public Monitoring get() {
        var builder = MonitoringClient.builder();

        ociConfig.region().ifPresent(builder::region);
        return builder.build(basicAuthenticationDetailsProvider);
    }
}
