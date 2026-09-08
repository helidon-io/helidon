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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;

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
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class SystemTagsManagerImpl implements SystemTagsManager {

    private final Map<String, String> systemTagPairs = new TreeMap<>();
    private final MetricsFactory metricsFactory;

    /*
    Defer provider-specific tag creation until the tags are requested. The system tags manager itself is often
    constructed while its owning meter registry is still being initialized.
     */
    private final LazyValue<List<Tag>> systemTags;
    private final Set<String> reservedTagNames;

    @Service.Inject
    SystemTagsManagerImpl(MetricsFactory metricsFactory) {
        this(metricsFactory,
             metricsFactory.metricsConfig());
    }

    private SystemTagsManagerImpl(MetricsFactory metricsFactory,
                                  MetricsConfig metricsConfig) {

        this.metricsFactory = Objects.requireNonNull(metricsFactory);
        systemTags = LazyValue.create(() ->
                                              systemTagPairs.entrySet().stream()
                                                      .map(entry -> this.metricsFactory.tagCreate(entry.getKey(),
                                                                                                  entry.getValue()))
                                                      .toList()); // global tags plus the app

        metricsConfig.tags().forEach(tag ->
                                             systemTagPairs.put(tag.key(), tag.value()));

        // Add a tag for the app name if there is an appName setting in config AND we have a setting
        // from somewhere for the tag name to use for recording the app name.

        metricsConfig.appTagName()
                .filter(Predicate.not(String::isBlank))
                .ifPresent(tagNameToUse ->
                                   metricsConfig.appName()
                                           .ifPresent(appNameToUse ->
                                                              systemTagPairs.put(tagNameToUse, appNameToUse))
                );

        Set<String> reservedTagNames = new HashSet<>();
        metricsConfig.appTagName().ifPresent(reservedTagNames::add);
        this.reservedTagNames = Set.copyOf(reservedTagNames);

    }

    static SystemTagsManagerImpl create(MetricsConfig metricsConfig) {
        return create(metricsConfig, Services.get(MetricsFactory.class));
    }

    static SystemTagsManagerImpl create(MetricsConfig metricsConfig, MetricsFactory metricsFactory) {
        return new SystemTagsManagerImpl(metricsFactory, metricsConfig);
    }

    @Override
    public Iterable<Tag> withoutSystemTags(Iterable<Tag> tags) {
        return without(tags, systemTagPairs.keySet());
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
    public Iterable<Tag> displayTags() {
        return Collections.unmodifiableCollection(systemTags.get());
    }

    @Override
    public Map<String, String> displayTagPairs() {
        return Collections.unmodifiableMap(systemTagPairs);
    }

    @Override
    public Collection<String> reservedTagNamesUsed(Collection<String> tagNames) {
        Set<String> reservedTagNamesUsed = new HashSet<>(tagNames);
        reservedTagNamesUsed.retainAll(reservedTagNames);
        return reservedTagNamesUsed;
    }
}
