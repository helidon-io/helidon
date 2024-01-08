/**
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.annotation.RegistryScope;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

/**
 * Producer of each type of registry.
 *
 * We cannot use a lazy value for the registry factory, because the factory can be updated with new metrics settings after
 * the first use (to closeAll the app registry) using runtime (not build-time) config.
 */
@ApplicationScoped
final class RegistryProducer {

    private RegistryProducer() {
    }

    @Produces
    @Default
    public static org.eclipse.microprofile.metrics.MetricRegistry getScopedRegistry(InjectionPoint injectionPoint) {
        Annotated annotated = injectionPoint == null ? null : injectionPoint.getAnnotated();
        RegistryScope scope = annotated == null ? null : annotated.getAnnotation(RegistryScope.class);
        return scope == null
                ? getApplicationRegistry()
                : RegistryFactory.getInstance().getRegistry(scope.scope());
    }

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

    @Produces
    public static RegistryFactory getRegistryFactory() {
        return RegistryFactory.getInstance();
    }

    /**
     * Clears Application registry. This is required for the Metric TCKs as they
     * all run on the same VM and must not interfere with each other.
     */
    static void clearApplicationRegistry() {
        MetricRegistry applicationRegistry = getApplicationRegistry();
        applicationRegistry.getNames().forEach(applicationRegistry::remove);
    }
}
