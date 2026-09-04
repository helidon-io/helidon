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
 * Deals with global and app-level tags to be included in the external representation for all metrics.
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
     * Returns the current {@link io.helidon.service.registry.ServiceRegistry}-backed instance, ignoring the provided
     * configuration.
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
     * Always returns empty because core metrics does not manage scope tags.
     *
     * @param candidateScope ignored; must not be {@code null}
     * @return empty
     * @deprecated Core metrics does not manage scope tags, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Optional<Tag> scopeTag(Optional<String> candidateScope) {
        Objects.requireNonNull(candidateScope);
        return Optional.empty();
    }

    /**
     * No-op, will be removed.
     *
     * @param tags original tags; must not be {@code null}
     * @param scope ignored; must not be {@code null}
     * @return provided tags
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Iterable<Map.Entry<String, String>> withScopeTag(Iterable<Map.Entry<String, String>> tags, String scope) {
        Objects.requireNonNull(tags);
        Objects.requireNonNull(scope);
        return tags;
    }

    /**
     * No-op, will be removed.
     *
     * @param tags original tags; must not be {@code null}
     * @param explicitScope ignored; must not be {@code null}
     * @return provided tags
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Iterable<Tag> withScopeTag(Iterable<Tag> tags, Optional<String> explicitScope) {
        Objects.requireNonNull(tags);
        Objects.requireNonNull(explicitScope);
        return tags;
    }

    /**
     * Returns an {@link java.lang.Iterable} of {@link io.helidon.metrics.api.Tag} omitting any system tags.
     *
     * @param tags tags to filter
     * @return tags without the system tags
     */
    Iterable<Tag> withoutSystemTags(Iterable<Tag> tags);

    /**
     * Returns tags without system tags. Scope tags are treated as ordinary tags.
     *
     * @param tags tags to filter; must not be {@code null}
     * @return non-null tags without system tags
     * @deprecated Use {@link #withoutSystemTags(Iterable)}. Scope tags are treated as ordinary tags, and this method will
     * be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Iterable<Tag> withoutSystemOrScopeTags(Iterable<Tag> tags) {
        Objects.requireNonNull(tags);
        return Objects.requireNonNull(withoutSystemTags(tags));
    }

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
     * No-op, will be removed.
     *
     * @param scope    ignored; must not be {@code null}
     * @param consumer ignored; must not be {@code null}
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default void assignScope(String scope, Function<Tag, ?> consumer) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(consumer);
    }

    /**
     * Always returns empty because core metrics does not assign scopes.
     *
     * @param candidateScope ignored; must not be {@code null}
     * @return empty
     * @deprecated Core metrics does not assign scopes, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Optional<String> effectiveScope(Optional<String> candidateScope) {
        Objects.requireNonNull(candidateScope);
        return Optional.empty();
    }

    /**
     * Always returns empty because core metrics does not assign scopes.
     *
     * @param explicitScope ignored; must not be {@code null}
     * @param tags ignored; must not be {@code null}
     * @return empty
     * @deprecated Core metrics does not assign scopes, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Optional<String> effectiveScope(Optional<String> explicitScope, Iterable<Tag> tags) {
        Objects.requireNonNull(explicitScope);
        Objects.requireNonNull(tags);
        return Optional.empty();
    }

    /**
     * Always returns empty because core metrics does not manage scope tags.
     *
     * @return empty
     * @deprecated Core metrics does not manage scope tags, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Optional<String> scopeTagName() {
        return Optional.empty();
    }
}
