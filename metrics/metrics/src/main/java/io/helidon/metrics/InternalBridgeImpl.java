/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.metrics;

import io.helidon.common.metrics.InternalBridge;
import io.helidon.common.metrics.InternalBridge.Metadata.MetadataBuilder;
import io.helidon.config.Config;

/**
 * Implements the metrics bridge interface based on MP Metrics 2.0.
 */
public class InternalBridgeImpl implements InternalBridge {

    @Override
    public RegistryFactory getRegistryFactory() {
        return io.helidon.metrics.RegistryFactory.getInstance();
    }

    @Override
    public RegistryFactory createRegistryFactory() {
        return io.helidon.metrics.RegistryFactory.create();
    }

    @Override
    public RegistryFactory createRegistryFactory(Config config) {
        return io.helidon.metrics.RegistryFactory.create(config);
    }

    @Override
    public MetricID.Factory getMetricIDFactory() {
        return new InternalMetricIDImpl.FactoryImpl();
    }

    @Override
    public MetadataBuilder.Factory getMetadataBuilderFactory() {
        return new InternalMetadataBuilderImpl.FactoryImpl();
    }

}
