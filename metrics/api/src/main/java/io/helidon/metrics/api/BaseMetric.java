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
package io.helidon.metrics.api;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;

/**
 * Helidon-required behavior for each type of metric implementation.
 */
public interface BaseMetric extends Metric {

    /**
     * @return the metadata for the metric
     */
    Metadata metadata();

    /**
     * Record that a previously-registered metric has been removed from the registry.
     */
    void markAsDeleted();

    /**
     * @return true if the metric has been removed from its registry; false if it is still registered
     */
    boolean isDeleted();

    /**
     * @return the name of the registry type in which the metric was registered
     */
    String registryType();
}
