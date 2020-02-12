/**
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import io.helidon.common.metrics.InternalBridge.MetricRegistry;

import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

/**
 * Producer of each type of registry.
 */
@ApplicationScoped
final class RegistryProducer {

    private static final io.helidon.metrics.RegistryFactory REGISTRY_FACTORY =
            io.helidon.metrics.RegistryFactory.getInstance();

    private RegistryProducer() {
    }

    @Produces
    public static MetricRegistry getDefaultRegistryInternal() {
        return getApplicationRegistryInternal();
    }

    @Produces
    @RegistryType(type = Type.APPLICATION)
    public static MetricRegistry getApplicationRegistryInternal() {
        return REGISTRY_FACTORY.getBridgeRegistry(Type.APPLICATION);
    }

    @Produces
    @RegistryType(type = Type.BASE)
    public static MetricRegistry getBaseRegistryInternal() {
        return REGISTRY_FACTORY.getBridgeRegistry(Type.BASE);
    }

    @Produces
    @RegistryType(type = Type.VENDOR)
    public static MetricRegistry getVendorRegistryInternal() {
        return REGISTRY_FACTORY.getBridgeRegistry(Type.VENDOR);
    }

    @Produces
    public static org.eclipse.microprofile.metrics.MetricRegistry getDefaultRegistry() {
        return getApplicationRegistry();
    }

    @Produces
    @RegistryType(type = Type.APPLICATION)
    public static org.eclipse.microprofile.metrics.MetricRegistry getApplicationRegistry() {
        return REGISTRY_FACTORY.getRegistry(Type.APPLICATION);
    }

    @Produces
    @RegistryType(type = Type.BASE)
    public static org.eclipse.microprofile.metrics.MetricRegistry getBaseRegistry() {
        return REGISTRY_FACTORY.getRegistry(Type.BASE);
    }

    @Produces
    @RegistryType(type = Type.VENDOR)
    public static org.eclipse.microprofile.metrics.MetricRegistry getVendorRegistry() {
        return REGISTRY_FACTORY.getRegistry(Type.VENDOR);
    }

    /**
     * Clears Application registry. This is required for the Metric TCKs as they
     * all run on the same VM and must not interfere with each other.
     */
    static void clearApplicationRegistry() {
        MetricRegistry applicationRegistry = getApplicationRegistryInternal();
        applicationRegistry.getNames().forEach(applicationRegistry::remove);
    }
}
