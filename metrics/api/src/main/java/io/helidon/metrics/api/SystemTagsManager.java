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
package io.helidon.metrics.api;

import java.util.Map;

import org.eclipse.microprofile.metrics.MetricID;

/**
 * Deals with global and app-level tags to be included in output for all metrics.
 */
public interface SystemTagsManager {

    /**
     * MicroProfile-specified tag for app-wide tag.
     */
    String APP_TAG = "_app";

    /**
     * Creates a new system tags manager using the provided metrics settings and saves it as
     * the initialized instance returned to subsequent invocations of {@link #instance()}.
     *
     * @param metricsSettings settings containing the global and app-level tags (if any)
     * @return new tags manager
     */
    static SystemTagsManager create(MetricsSettings metricsSettings) {
        return SystemTagsManagerImpl.create(metricsSettings);
    }

    /**
     * Returns the initialized instance of the tags manager.
     *
     * @return current instance of the tags manager
     */
    static SystemTagsManager instance() {
        return SystemTagsManagerImpl.instance();
    }

    /**
     * Returns a single iterator over the explicit tags in the metric ID plus any global and app-level tags.
     *
     * @param metricID metric ID possibly containing explicit tag settings
     * @return iterator over all tags, explicit and global and app-level
     */
    Iterable<Map.Entry<String, String>> allTags(MetricID metricID);

    /**
     * Returns a single iterator over the explicit tags in the provided map plus any global and app-level tags.
     *
     * @param explicitTags map containing explicitly-defined tags for a metric
     * @return iterator over all tags, explicit and global and app-level
     */
    Iterable<Map.Entry<String, String>> allTags(Map<String, String> explicitTags);
}
