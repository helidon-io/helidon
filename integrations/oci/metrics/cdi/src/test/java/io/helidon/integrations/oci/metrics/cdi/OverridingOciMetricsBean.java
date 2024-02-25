/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import javax.annotation.Priority;
import javax.enterprise.inject.Alternative;
import javax.inject.Singleton;
import javax.interceptor.Interceptor;

import io.helidon.config.Config;
import io.helidon.integrations.oci.metrics.OciMetricsSupport;

import com.oracle.bmc.monitoring.Monitoring;

/**
 * Example of an overriding bean implementation to test that overrides work. (See
 * {@link io.helidon.integrations.oci.metrics.cdi.TestOverridingBean}.)
 */
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
@Alternative
@Singleton
class OverridingOciMetricsBean extends OciMetricsBean {

    @Override
    protected String configKey() {
        return "oci.metrics";
    }

    @Override
    protected OciMetricsSupport.Builder ociMetricsSupportBuilder(Config rootConfig,
                                                                 Config ociMetricsConfig,
                                                                 Monitoring monitoring) {
        OciMetricsSupport.Builder result = super.ociMetricsSupportBuilder(rootConfig, rootConfig, monitoring);
        // Example using synonyms for two of the config keys.
        ociMetricsConfig.get("product").asString().ifPresent(result::namespace);
        ociMetricsConfig.get("fleet").asString().ifPresent(result::resourceGroup);
        return result;
    }
}
