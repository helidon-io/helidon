/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.service.registry.Services;

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
     * Creates a new system tags manager using the provided metrics settings and metrics factory.
     *
     * @param metricsConfig settings containing the global and app-level tags (if any)
     * @param metricsFactory factory to use for creating tags
     * @return new tags manager
     */
    static SystemTagsManager create(MetricsConfig metricsConfig, MetricsFactory metricsFactory) {
        return SystemTagsManagerImpl.create(metricsConfig, metricsFactory);
    }

    /**
     * Returns the initialized instance of the tags manager.
     *
     * @return current instance of the tags manager
     * @deprecated since 27.0.0, for removal. Use
     * {@link io.helidon.service.registry.Services#get(java.lang.Class) Services.get(SystemTagsManager.class)} for the
     * shared system tags manager, or {@link #create(MetricsConfig)} for a non-global instance.
     */
    @Deprecated(since = "27.0.0", forRemoval = true)
    static SystemTagsManager instance() {
        return Services.get(SystemTagsManager.class);
    }

    /**
     * This method is now a no-op, it simply returns the current {@link io.helidon.service.registry.ServiceRegistry}
     * backed instance.
     *
     * @param metricsConfig ignored; must not be {@code null}
     * @return tags manager from the service registry
     * @deprecated use
     * {@link io.helidon.service.registry.Services#get(java.lang.Class) Services.get(SystemTagsManager.class)} instead
     */
    @Deprecated(since = "27.0.0", forRemoval = true)
    static SystemTagsManager instance(MetricsConfig metricsConfig) {
        Objects.requireNonNull(metricsConfig);
        return Services.get(SystemTagsManager.class);
    }

    /**
     * No-op retained for compatibility. The service registry owns one immutable shared system tags manager.
     *
     * @param ignoredChangeListener ignored listener; must not be {@code null}
     * @deprecated since 27.0.0, for removal. Obtain the shared manager from
     * {@link io.helidon.service.registry.Services#get(java.lang.Class) Services.get(SystemTagsManager.class)}. To use
     * different metrics configuration, create a non-global manager using {@link #create(MetricsConfig)}.
     */
    @Deprecated(since = "27.0.0", forRemoval = true)
    static void onChange(Consumer<SystemTagsManager> ignoredChangeListener) {
        Objects.requireNonNull(ignoredChangeListener);
    }

    /**
     * Returns a scope tag so long as the candidate scope or configured default scope are present and the scope tag name
     * is configured.
     *
     * @param candidateScope candidate scope value
     * @return {@link io.helidon.metrics.api.Tag} representing the scope if suitable; empty otherwise
     */
    Optional<Tag> scopeTag(Optional<String> candidateScope);

    /**
     * Augments map entries (tag names and values) with, possibly, one more for the scope (if configured that way).
     *
     * @param tags original tags
     * @param scope the scope value
     * @return augmented iterable including, if appropriate, a scope tag entry
     */
    Iterable<Map.Entry<String, String>> withScopeTag(Iterable<Map.Entry<String, String>> tags, String scope);

    /**
     * Augments, if necessary, the provided tags with an additional tag with the scope tag name and value from the explicit
     * scope provided, an existing tag, or the default scope value, if configured.
     *
     * @param tags original tags
     * @param explicitScope explicit scope setting if available
     * @return tags containing a tag for the scope
     */
    Iterable<Tag> withScopeTag(Iterable<Tag> tags, Optional<String> explicitScope);

    /**
     * Returns an {@link java.lang.Iterable} of {@link io.helidon.metrics.api.Tag} omitting any system tags but including
     * the scope tag, if these appear in the provided tags.
     *
     * @param tags tags to filter
     * @return tags without the system tags
     */
    Iterable<Tag> withoutSystemTags(Iterable<Tag> tags);

    /**
     * Returns an {@link java.lang.Iterable} of {@link io.helidon.metrics.api.Tag} omitting system and scope tags.
     *
     * @param tags tags to filter
     * @return tags without system or scope tags
     */
    Iterable<Tag> withoutSystemOrScopeTags(Iterable<Tag> tags);

    /**
     * Returns an {@link java.lang.Iterable} of {@link io.helidon.metrics.api.Tag} representing the any system tags
     * configured for display (for example, an app tag or global tags set through configuration).
     *
     * @return system tags
     */
    Iterable<Tag> displayTags();

    /**
     * Returns name/value pairs of system tags, avoiding constructing the provider's {@link io.helidon.metrics.api.Tag}
     * implementations for each.
     *
     * @return system tag name/value pairs
     */
    default Map<String, String> displayTagPairs() {
        Map<String, String> result = new TreeMap<>();
        displayTags().forEach(tag -> result.put(tag.key(), tag.value()));
        return result;
    }

    /**
     * Scans the provided tag names and throws an exception if any is a reserved tag name.
     *
     * @param tagNames tag names
     * @return reserved tag names present in the provided tag names
     */
    Collection<String> reservedTagNamesUsed(Collection<String> tagNames);

    /**
     * Invokes the specified consumer with the scope tag name setting from the configuration (if present) and the
     * provided scope value. This method is most useful to assign a tag to a meter if configuration implies that.
     *
     * @param scope    scope value to use
     * @param consumer uses the tag and scope in some appropriate way
     */
    void assignScope(String scope, Function<Tag, ?> consumer);

    /**
     * Returns the effective scope, given the provided candidate scope combined with any default scope value in the
     * configuration which initialized this manager.
     *
     * @param candidateScope candidate scope
     * @return effective scope, preferring the candidate and falling back to the default; empty if neither is present
     */
    Optional<String> effectiveScope(Optional<String> candidateScope);

    /**
     * Returns the effective scope, given the provided explicit setting and tags (which might specify the scope) combined with
     * any default scope value in the configuration which was used to initialize this system tags manager.
     *
     * @param explicitScope explicit scope to use (if present)
     * @param tags tag which might specify the scope using the configured scope tag name
     * @return effective scope; empty if none available from the arguments or the system default
     */
    Optional<String> effectiveScope(Optional<String> explicitScope, Iterable<Tag> tags);

    /**
     * Returns the scope tag name derived from configuration.
     *
     * @return scope tag name; empty if not set
     */
    Optional<String> scopeTagName();
}
