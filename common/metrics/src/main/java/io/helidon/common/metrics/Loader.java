/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.common.metrics;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Uses the Java service loader mechanism to find an implementation of the
 * internal bridge and offer access to the various factories that implementation
 * provides.
 */
class Loader {
    private static final io.helidon.common.metrics.InternalBridge BRIDGE = loadInternalBridge();

    static io.helidon.common.metrics.InternalBridge internalBridge() {
        return BRIDGE;
    }

    static io.helidon.common.metrics.InternalBridge.MetricRegistry.RegistryFactory registryFactory() {
        return BRIDGE.getRegistryFactory();
    }

    static io.helidon.common.metrics.InternalBridge.MetricID.Factory metricIDFactory() {
        return BRIDGE.getMetricIDFactory();
    }

    static io.helidon.common.metrics.InternalBridge.Metadata.MetadataBuilder.Factory metadataBuilderFactory() {
        return BRIDGE.getMetadataBuilderFactory();
    }

    private static io.helidon.common.metrics.InternalBridge loadInternalBridge() {
        for (Iterator<io.helidon.common.metrics.InternalBridge> it =
                ServiceLoader.load(io.helidon.common.metrics.InternalBridge.class).iterator();
                it.hasNext();) {
            return it.next();
        }
        throw new RuntimeException("Could not find implementation of bridge "
                + io.helidon.common.metrics.InternalBridge.class.getName() + " to load");
    }

    private Loader() {
    }

}
