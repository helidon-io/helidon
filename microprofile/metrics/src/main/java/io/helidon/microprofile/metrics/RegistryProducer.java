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
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryScope;

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

//    @Produces
    public static org.eclipse.microprofile.metrics.MetricRegistry getDefaultRegistry() {
        return getApplicationRegistry();
    }

    // TODO - uncomment the following two lines once MP metrics makes @RegistryScope a qualifier.
//    @Produces
//    @RegistryScope()
    public static org.eclipse.microprofile.metrics.MetricRegistry getApplicationRegistry() {
        return RegistryFactory.getInstance().getRegistry(MetricRegistry.APPLICATION_SCOPE);
    }

    @Produces
    public static MetricRegistry getRegistry(InjectionPoint injectionPoint) {
        RegistryScope registryScope = injectionPoint.getAnnotated()
                                                    .getAnnotation(RegistryScope.class);
        String scope = registryScope != null && !registryScope.scope().isBlank()
                ? registryScope.scope()
                : MetricRegistry.APPLICATION_SCOPE;
        return RegistryFactory.getInstance().getRegistry(scope);
    }

    // TODO add the following back in (and make the preceding getApplicationRegistry like these two)
    // once MP metrics makes RegistryScope a qualifier.
    //
//    @Produces
//    @RegistryScope(scope = MetricRegistry.BASE_SCOPE)
//    public static org.eclipse.microprofile.metrics.MetricRegistry getBaseRegistry() {
//        return RegistryFactory.getInstance().getRegistry(MetricRegistry.BASE_SCOPE);
//    }
//
//    @Produces
//    @RegistryScope(scope = MetricRegistry.VENDOR_SCOPE)
//    public static org.eclipse.microprofile.metrics.MetricRegistry getVendorRegistry() {
//        return RegistryFactory.getInstance().getRegistry(MetricRegistry.VENDOR_SCOPE);
//    }

    /**
     * Return the base registry.
     *
     * @return base registry
     */
    static MetricRegistry getBaseRegistry() {
        return RegistryFactory.getInstance().getRegistry(MetricRegistry.BASE_SCOPE);
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
