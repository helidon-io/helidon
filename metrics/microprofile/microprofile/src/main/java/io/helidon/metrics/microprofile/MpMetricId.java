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
package io.helidon.metrics.microprofile;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Tag;

public class MpMetricId extends MetricID {

    // Functionally equivalent to the superclass MetricID, so we don't need equals or hashCode to account for any private
    // fields here.

    private Tags fullTags = Tags.empty();
    private final Meter.Id meterId;

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

    public String name() {
        return getName();
    }

    Meter.Id meterId() {
        return meterId;
    }
}
