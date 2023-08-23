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
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Deals with global, app-level, and scope to be included in the external representation (output and IDs in delegate
 * meter registries) for all metrics.
 */
public interface SystemTagsManager {

    /**
     * Creates a new system tags manager using the provided metrics settings.
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
     * Creates a new system tags manager using the provide metrics settings, saving the new instance as the initialized
     * singleton which will be returned to subsequent invocations of {@link #instance()}.
     *
     * @param metricsConfig settings containing the global and app-level tags (if any)
     * @return new (and saved) tags manager
     */
    static SystemTagsManager instance(MetricsConfig metricsConfig) {
        return SystemTagsManagerImpl.instance(metricsConfig);
    }

    /**
     * Returns a single iterator over the explicit tags in the meter ID plus any global and app tags.
     *
     * @param meterId meter ID possibly containing explicit tag settings
     * @param scope   the registry scope
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

    /**
     * Returns a single iterator over the explicit tags in the provided {@link java.lang.Iterable}, plus any global
     * and app tags, plus a tag for the specified scope (if the system tags manager has been initialized
     * with a scope tag name).
     *
     * @param explicitTags iterable over the key/value pairs for tags
     * @param scope        scope value
     * @return iterator over all tags, explicit and global and app
     */
    Iterable<Map.Entry<String, String>> allTags(Iterable<Map.Entry<String, String>> explicitTags, String scope);

    /**
     * Invokes the specified consumer with the scope tag name setting from the configuration (if present) and the
     * provided scope value. This method is most useful to assign a tag to a meter if configuration implies that.
     *
     * @param scope    scope value to use
     * @param consumer uses the tag and scope in some appropriate way
     */
    void assignScope(String scope, BiConsumer<String, String> consumer);

    /**
     * Returns the effective scope, given the provided candidate scope combined with any default scope value in the
     * configuration which initialized this manager.
     *
     * @param candidateScope candidate scope
     * @return effective scope, preferring the candidate and falling back to the default; empty if neither is present
     */
    Optional<String> effectiveScope(Optional<String> candidateScope);

    /**
     * Returns the scope tag name derived from configuration.
     *
     * @return scope tag name; empty if not set
     */
    Optional<String> scopeTagName();
}
