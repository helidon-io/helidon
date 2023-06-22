/*
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

package io.helidon.metrics;

import io.helidon.metrics.api.AbstractMetric;

import io.micrometer.core.instrument.Tags;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Base for our implementations of various metrics.
 */
abstract class MetricImpl extends AbstractMetric implements HelidonMetric {
    static final double[] DEFAULT_PERCENTILES = {0.5, 0.75, 0.95, 0.98, 0.99, 0.999};
    static final int DEFAULT_PERCENTILE_PRECISION = 3;

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

    protected static Tags tags(Tag... tags) {
        Tags result = Tags.empty();
        for (Tag tag : tags) {
            result = result.and(tag.getTagName(), tag.getTagValue());
        }
        return result;
    }

}
