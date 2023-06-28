/**
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import io.helidon.metrics.api.RegistryFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

/**
 * Producer of each type of registry.
 *
 * We cannot use a lazy value for the registry factory, because the factory can be updated with new metrics settings after
 * the first use (to clear the app registry) using runtime (not build-time) config.
 */
@ApplicationScoped
final class RegistryProducer {

    private RegistryProducer() {
    }

    @Produces
    public static org.eclipse.microprofile.metrics.MetricRegistry getDefaultRegistry() {
        return getApplicationRegistry();
    }

    // TODO Once RegistryScope becomes a qualifier, use it instead of RegistryType.
    @Produces
    @RegistryType(type = Type.APPLICATION)
    public static org.eclipse.microprofile.metrics.MetricRegistry getApplicationRegistry() {
        return RegistryFactory.getInstance().getRegistry(MetricRegistry.APPLICATION_SCOPE);
    }

    @Produces
    // TODO Once RegistryScope becomes a qualifier, use it instead of RegistryType.
    @RegistryType(type = Type.BASE)
    public static org.eclipse.microprofile.metrics.MetricRegistry getBaseRegistry() {
        return RegistryFactory.getInstance().getRegistry(MetricRegistry.BASE_SCOPE);
    }

    // TODO Once RegistryScope becomes a qualifier, use it instead of RegistryType.
    @Produces
    @RegistryType(type = Type.VENDOR)
    public static org.eclipse.microprofile.metrics.MetricRegistry getVendorRegistry() {
        return RegistryFactory.getInstance().getRegistry(MetricRegistry.VENDOR_SCOPE);
    }

    /**
     * Clears Application registry. This is required for the Metric TCKs as they
     * all run on the same VM and must not interfere with each other.
     */
    static void clearApplicationRegistry() {
        MetricRegistry applicationRegistry = getApplicationRegistry();
        applicationRegistry.removeMatching(MetricFilter.ALL);
    }
}
