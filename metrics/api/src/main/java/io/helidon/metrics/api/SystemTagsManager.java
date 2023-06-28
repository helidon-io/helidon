/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
 * Deals with global, app-level, and scope to be included in the external representation (output and IDs in delegate
 * meter registries) for all metrics.
 */
public interface SystemTagsManager {

    /**
     * Creates a new system tags manager using the provided metrics settings, saving the new instance as the initialized
     * singleton which will be returned to subsequent invocatinos of {@link #instance()}.
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
     * Returns a single iterator over the explicit tags in the metric ID plus any global and app tags.
     *
     * @param metricID metric ID possibly containing explicit tag settings
     * @param scope the registry scope
     * @return iterator over all tags, explicit and global and app
     */
    Iterable<Map.Entry<String, String>> allTags(MetricID metricID, String scope);

    /**
     * Returns a single iterator over the explicit tags in the provided map plus any global and app tags.
     *
     * @param explicitTags map containing explicitly-defined tags for a metric
     * @param scope registry scope
     * @return iterator over all tags, explicit and global and app
     */
    Iterable<Map.Entry<String, String>> allTags(Map<String, String> explicitTags, String scope);

    /**
     * Returns a single iterator over the explicit tags in the provided {@link java.lang.Iterable}, plus any global
     * and app tags, plus a tag for the specified scope (if the system tags manager has been initialized
     * with a scope tag name).
     * @param explicitTags iterable over the key/value pairs for tags
     * @param scope scope value
     * @return iterator over all tags, explicit and global and app
     */
    Iterable<Map.Entry<String, String>> allTags(Iterable<Map.Entry<String, String>> explicitTags, String scope);

    /**
     * Returns a single iterator over the explicit tags in the provided {@link java.lang.Iterable}, plus any global
     * and app tags, <em>without</em>> a tag for scope.
     *
     * @param explicitTags iterable over the key/value pairs for tags
     * @return iterator over all tags, explicit and global and app
     * @deprecated use a variant which accepts {@code scope} instead
     */
    @Deprecated(since = "4.0.0", forRemoval = true)
    Iterable<Map.Entry<String, String>> allTags(Iterable<Map.Entry<String, String>> explicitTags);

    /**
     * Returns a single iterator over the explicit tags in the provided {@link org.eclipse.microprofile.metrics.MetricID}, plus
     * any global and app tags <em>without</em> scope.
     *
     * @param metricId metric ID
     * @return iterator over all tags, explicit and global and app, without a tag for scope
     * @deprecated use a variant which accepts {@code scope} instance
     */
    @Deprecated(since = "4.0.0", forRemoval = true)
    Iterable<Map.Entry<String, String>> allTags(MetricID metricId);

    /**
     * Creates a new {@link org.eclipse.microprofile.metrics.MetricID} using the original ID and adding the system tags.
     *
     * @param original original metric ID
     * @param scope scope to use in augmenting the tags
     * @return augmented metric ID
     */
    MetricID metricIdWithAllTags(MetricID original, String scope);
}
