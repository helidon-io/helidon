/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.microprofile.metrics.DefaultMetadata;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

/**
 * Class HelidonMetadata. In MP Metrics 2.0, {@link org.eclipse.microprofile.metrics.Metadata}
 * is now immutable and a builder was added. This class allows creation of metadata
 * directly using a constructor to avoid switching to a builder in dozens of locations.
 * Can be used from other packages unfortunately.
 */
public class HelidonMetadata extends DefaultMetadata {

    /**
     * Construct immutable metadata.
     *
     * @param name Metric name.
     * @param type Metric type.
     */
    public HelidonMetadata(String name, MetricType type) {
        super(name, null, null, type, MetricUnits.NONE, true);
    }

    /**
     * Construct immutable metadata.
     *
     * @param name Metric name.
     * @param displayName Metric display name.
     * @param description Metric description.
     * @param type Metric type.
     * @param unit Metric unit.
     * @param reusable Reusable flag.
     */
    public HelidonMetadata(String name, String displayName, String description, MetricType type,
                           String unit, boolean reusable) {
        super(name, displayName, description, type, unit, reusable);
    }

    /**
     * Construct immutable metadata.
     *
     * @param name Metric name.
     * @param displayName Metric display name.
     * @param description Metric description.
     * @param type Metric type.
     * @param unit Metric unit.
     */
    public HelidonMetadata(String name, String displayName, String description, MetricType type, String unit) {
        super(name, displayName, description, type, unit, true);
    }
}
