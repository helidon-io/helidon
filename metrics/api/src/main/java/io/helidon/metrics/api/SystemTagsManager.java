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

/**
 * Deals with global, app-level, and scope to be included in the external representation (output and IDs in delegate
 * meter registries) for all metrics.
 */
public interface SystemTagsManager {

    /**
     * Creates a new system tags manager using the provided metrics settings, saving the new instance as the initialized
     * singleton which will be returned to subsequent invocatinos of {@link #instance()}.
     *
     * @param metricsConfig settings containing the global and app-level tags (if any)
     * @return new tags manager
     */
    static SystemTagsManager create(MetricsConfig metricsConfig) {
        return SystemTagsManagerImpl.create(metricsConfig);
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
     * Returns a single iterator over the explicit tags in the meter ID plus any global and app tags.
     *
     * @param meterId meter ID possibly containing explicit tag settings
     * @param scope the registry scope
     * @return iterator over all tags, explicit and global and app
     */
    Iterable<Tag> allTags(Meter.Id meterId, String scope);

    /**
     * Returns a single iterator over the explicit tags in the provided {@link io.helidon.metrics.api.Meter.Id}, plus
     * any global and app tags <em>without</em> scope.
     *
     * @param meterId meter ID
     * @return iterator over all tags, explicit and global and app, without a tag for scope
     */
    Iterable<Tag> allTags(Meter.Id meterId);

}
