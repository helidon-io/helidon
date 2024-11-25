/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import io.helidon.metrics.spi.MetricsProgrammaticConfig;

/**
 * Captures and makes available for output any system tag settings to be applied when metric IDs are output.
 * <P>
 * In MP, the config might contain the config key {@code mp.metrics.tags}. In SE, the config might be {@code metrics.tags},
 * either of which can be a string of the form {@code tag1=value1,tag2=value2,...}.
 * </P>
 * <p>
 * Further, the MP config key {@code mp.metrics.appName} or the SE config key {metrics.appName} can convey
 * an application name which will add a tag conveying the app name to each metric ID written to output.
 * </p>
 */
class SystemTagsManagerImpl implements SystemTagsManager {

    private static SystemTagsManagerImpl instance = new SystemTagsManagerImpl();

    private static final Collection<Consumer<SystemTagsManager>> ON_CHANGE_SUBSCRIBERS = new ArrayList<>();

    private final List<Tag> systemTags; // global tags plus the app tag, if any specified
    private final Set<String> systemTagNames = new HashSet<>(); // tag names for global tags and app
    private final Set<String> systemAndScopeTagNames = new HashSet<>(); // tag names for globa tags, app, and scope
    private String scopeTagName;
    private String defaultScopeValue;

    private SystemTagsManagerImpl(MetricsConfig metricsConfig) {

        metricsConfig.tags().forEach(tag -> systemTagNames.add(tag.key()));
        systemAndScopeTagNames.addAll(systemTagNames);
        metricsConfig.scoping().tagName().ifPresent(systemAndScopeTagNames::add);

        List<Tag> result = new ArrayList<>(metricsConfig.tags());

        // Add a tag for the app name if there is an appName setting in config AND we have a setting
        // from somewhere for the tag name to use for recording the app name.

        metricsConfig.appTagName()
                .filter(Predicate.not(String::isBlank))
                .ifPresent(tagNameToUse ->
                                   metricsConfig.appName()
                                           .ifPresent(appNameToUse -> result.add(Tag.create(tagNameToUse, appNameToUse)))
                );

        systemTags = List.copyOf(result);

        // Set the scope tag, if appropriate.
        metricsConfig.scoping().tagName()
                .ifPresent(tagNameToUse -> scopeTagName = tagNameToUse);
        defaultScopeValue = metricsConfig.scoping().defaultValue().orElse(null);
    }

    static void onChange(Consumer<SystemTagsManager> subscriber) {
        ON_CHANGE_SUBSCRIBERS.add(subscriber);
    }

    // for testing
    private SystemTagsManagerImpl() {
        systemTags = List.of();
        scopeTagName = "scope";
    }

    /**
     * Returns the singleton instance of the system tags manager.
     *
     * @return the singleton instance
     */
    public static SystemTagsManager instance() {
        return instance;
    }

    static SystemTagsManagerImpl create(MetricsConfig metricsConfig) {
        instance = createWithoutSaving(metricsConfig);
        return instance;
    }

    static SystemTagsManagerImpl instance(MetricsConfig metricsConfig) {
        instance = createWithoutSaving(metricsConfig);
        ON_CHANGE_SUBSCRIBERS.forEach(sub -> sub.accept(instance()));
        return instance;
    }

    static SystemTagsManagerImpl createWithoutSaving(MetricsConfig metricsConfig) {
        return new SystemTagsManagerImpl(metricsConfig);
    }

    @Override
    public Optional<Tag> scopeTag(Optional<String> candidateScope) {
        return scopeTagName == null
                ? Optional.empty()
                : effectiveScope(candidateScope)
                        .map(sc -> Tag.create(scopeTagName,
                                              sc));
    }

    @Override
    public Iterable<Tag> withoutSystemTags(Iterable<Tag> tags) {
        return without(tags, systemTagNames);
    }

    @Override
    public Iterable<Tag> withoutSystemOrScopeTags(Iterable<Tag> tags) {
        return without(tags, systemAndScopeTagNames);
    }

    private Iterable<Tag> without(Iterable<Tag> tags, Collection<String> unwantedTagNames) {
        if (unwantedTagNames.isEmpty()) {
            return tags;
        }
        List<Tag> result = new ArrayList<>();
        tags.forEach(tag -> {
            if (!unwantedTagNames.contains(tag.key())) {
                result.add(tag);
            }
        });
        return result;
    }
    @Override
    public Iterable<Tag> withScopeTag(Iterable<Tag> tags, Optional<String> explicitScope) {
        if (scopeTagName == null) {
            return tags;
        }
        Map<String, Tag> tagsMap = new TreeMap<>();

        if (defaultScopeValue != null) {
            tagsMap.put(scopeTagName,
                        Tag.create(scopeTagName,
                                   defaultScopeValue));
        }

        tags.forEach(tag -> tagsMap.put(tag.key(), // If scope is set in a tag, the tag's value overrides the default in the map.
                                        tag));

        explicitScope.ifPresent(s -> tagsMap.put(scopeTagName,
                                                 Tag.create(scopeTagName,
                                                            explicitScope.get())));

        return tagsMap.values();
    }

    @Override
    public Iterable<Map.Entry<String, String>> withScopeTag(Iterable<Map.Entry<String, String>> tags, String scope) {
        if (scopeTagName == null) {
            return tags;
        }
        Map<String, String> result = new TreeMap<>();
        tags.forEach((tag -> result.put(tag.getKey(), tag.getValue())));
        result.put(scopeTagName, scope);
        return result.entrySet();
    }

    @Override
    public Iterable<Tag> displayTags() {
        return List.copyOf(systemTags);
    }

    @Override
    public void assignScope(String validScope, Function<Tag, ?> tagSetter) {
        if (scopeTagName != null) {
            tagSetter.apply(Tag.create(scopeTagName, validScope));
        }
    }

    @Override
    public Optional<String> effectiveScope(Optional<String> candidateScope) {
        return candidateScope.isEmpty() && defaultScopeValue == null
                ? Optional.empty()
                : Optional.of(candidateScope.orElse(defaultScopeValue));
    }

    @Override
    public Optional<String> effectiveScope(Optional<String> explicitScope, Iterable<Tag> tags) {
        return explicitScope
                .or(() -> scopeFromTags(tags))
                .or(() -> Optional.ofNullable(defaultScopeValue));
    }

    @Override
    public Optional<String> scopeTagName() {
        return Optional.ofNullable(scopeTagName);
    }

    @Override
    public Collection<String> reservedTagNamesUsed(Collection<String> tagNames) {
        Set<String> reservedTagNamesUsed = new HashSet<>(tagNames);
        reservedTagNamesUsed.retainAll(MetricsProgrammaticConfig.instance().reservedTagNames());
        return reservedTagNamesUsed;
    }

    private Optional<String> scopeFromTags(Iterable<Tag> tags) {
        return (scopeTagName != null)
                ? StreamSupport.stream(tags.spliterator(), false)
                .filter(tag -> tag.key().equals(scopeTagName))
                .findAny()
                .map(Tag::value)
                : Optional.empty();
    }

}
