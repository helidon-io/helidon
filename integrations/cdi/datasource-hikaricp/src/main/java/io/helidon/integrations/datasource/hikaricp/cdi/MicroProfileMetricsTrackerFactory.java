/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.datasource.hikaricp.cdi;

import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

@ApplicationScoped
class MicroProfileMetricsTrackerFactory implements MetricsTrackerFactory {

    private final MetricRegistry registry;

    @Deprecated // required by CDI; not for general use
    MicroProfileMetricsTrackerFactory() {
        super();
        this.registry = null;
    }

    @Inject
    MicroProfileMetricsTrackerFactory(@RegistryType(type = MetricRegistry.Type.VENDOR) final MetricRegistry registry) {
        super();
        this.registry = registry;
    }

    @Override
    public IMetricsTracker create(final String poolName, final PoolStats poolStats) {
        return new MicroProfileMetricsTracker(poolName, poolStats, this.registry);
    }

}
