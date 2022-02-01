/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.StringJoiner;

import org.eclipse.microprofile.metrics.MetricID;

/**
 * Simple implementation of {@link MetricWorkItem} which simply exposes its {@code MetricID} and {@code Metric}.
 */
record BasicMetricWorkItem(MetricID metricID, org.eclipse.microprofile.metrics.Metric metric)
        implements MetricWorkItem {

    static <T extends org.eclipse.microprofile.metrics.Metric>
    BasicMetricWorkItem create(MetricID metricID,
                               org.eclipse.microprofile.metrics.Metric metric) {
        return new BasicMetricWorkItem(metricID, metric);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BasicMetricWorkItem that = (BasicMetricWorkItem) o;
        return metricID.equals(that.metricID) && metric.equals(that.metric);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricID, metric);
    }

    @Override
    public String toString() {
        return new StringJoiner(", " + System.lineSeparator(),
                                BasicMetricWorkItem.class.getSimpleName() + "[",
                                "]")
                .add("metricID=" + metricID)
                .add("metric=" + metric)
                .toString();
    }
}
