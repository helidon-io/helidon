/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Creates a new instance of the metric ID implementation.
 */
public class MpMetricId extends MetricID {

    private Tags fullTags = Tags.empty();
    private final Meter.Id meterId;

    /**
     * Creates a new instance of the metric ID.
     *
     * @param name metric name
     * @param tags tags
     * @param automaticTags automatically-added tags (e.g., app ID or scope)
     * @param baseUnit unit for the metric
     * @param description description of the metric
     * @param type meter type for the metric
     */
    MpMetricId(String name, Tag[] tags, Tag[] automaticTags, String baseUnit, String description, Meter.Type type) {
        super(name, tags);
        for (Tag tag : tags) {
            fullTags = fullTags.and(io.micrometer.core.instrument.Tag.of(tag.getTagName(), tag.getTagValue()));
        }
        for (Tag tag : automaticTags) {
            fullTags = fullTags.and(io.micrometer.core.instrument.Tag.of(tag.getTagName(), tag.getTagValue()));
        }

        meterId = new Meter.Id(name, fullTags, baseUnit, description, type);
    }

    /**
     * Returns the metric name.
     *
     * @return metric name
     */
    public String name() {
        return getName();
    }

    /**
     * Returns all tags, including those "hidden" for differentiating scope.
     *
     * @return returns all tags
     */
    public Tags fullTags() {
        return fullTags;
    }

    /**
     * Returns the underlying implementation's meter ID.
     *
     * @return underlying meter ID
     */
    Meter.Id meterId() {
        return meterId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        MpMetricId that = (MpMetricId) o;
        return fullTags.equals(that.fullTags) && meterId.equals(that.meterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fullTags, meterId);
    }
}
