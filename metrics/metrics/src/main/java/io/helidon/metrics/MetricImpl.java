/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.metrics;

import io.helidon.metrics.api.AbstractMetric;

import org.eclipse.microprofile.metrics.Metadata;

/**
 * Base for our implementations of various metrics.
 */
abstract class MetricImpl extends AbstractMetric implements HelidonMetric {
    // Efficient check from interceptors to see if the metric is still valid
    private boolean isDeleted;

    MetricImpl(String registryType, Metadata metadata) {
        super(registryType, metadata);
    }


    @Override
    public String getName() {
        return metadata().getName();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{"
                + "registryType='" + registryType() + '\''
                + ", metadata=" + metadata()
                + toStringDetails()
                + '}';
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    protected String toStringDetails() {
        return "";
    }

}
