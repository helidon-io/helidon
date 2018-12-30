/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import io.helidon.metrics.RegistryFactory;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

/**
 * Producer of each type of registry.
 */
@ApplicationScoped
public class RegistryProducer {

    private static Supplier<RegistryFactory> factorySupplier = RegistryFactory.getRegistryFactory();

    private RegistryProducer() {
    }

    @Produces
    public static MetricRegistry getDefaultRegistry() {
        return getApplicationRegistry();
    }

    @Produces
    @RegistryType(type = Type.APPLICATION)
    public static MetricRegistry getApplicationRegistry() {
        return factorySupplier.get().getRegistry(Type.APPLICATION);
    }

    @Produces
    @RegistryType(type = Type.BASE)
    public static MetricRegistry getBaseRegistry() {
        return factorySupplier.get().getRegistry(Type.BASE);
    }

    @Produces
    @RegistryType(type = Type.VENDOR)
    public static MetricRegistry getVendorRegistry() {
        return factorySupplier.get().getRegistry(Type.VENDOR);
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
