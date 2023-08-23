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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

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

    private final List<Tag> systemTags;
    private String scopeTagName;
    private String defaultScopeValue;

    private SystemTagsManagerImpl(MetricsConfig metricsConfig) {
        List<Tag> result = new ArrayList<>(metricsConfig.globalTags());

        // Add a tag for the app name if there is an appName setting in config AND we have a setting
        // from somewhere for the tag name to use for recording the app name.

        MetricsProgrammaticConfig.instance()
                .appTagName()
                .filter(Predicate.not(String::isBlank))
                .ifPresent(tagNameToUse ->
                                   metricsConfig.appName()
                                           .ifPresent(appNameToUse -> result.add(Tag.create(tagNameToUse, appNameToUse)))
                );

        systemTags = List.copyOf(result);

        // Set the scope tag, if appropriate. Prefer a setting from the programmatic config, then look at the
        // user-settable config.
        if (metricsConfig.scoping().tagEnabled()) {
            MetricsProgrammaticConfig.instance()
                    .scopeTagName()
                    .or(metricsConfig.scoping()::tagName)
                    .ifPresent(tagNameToUse -> scopeTagName = tagNameToUse);
        }
        defaultScopeValue = metricsConfig.scoping().defaultValue().orElse(null);
    }

    // for testing
    private SystemTagsManagerImpl() {
        systemTags = List.of();
        scopeTagName = "_testScope_";
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
        return instance;
    }

    static SystemTagsManagerImpl createWithoutSaving(MetricsConfig metricsConfig) {
        return new SystemTagsManagerImpl(metricsConfig);
    }

    /**
     * Returns an {@link java.lang.Iterable} of the implied type representing the provided scope <em>if</em> scope tagging
     * is active: the scope tag name is non-null and non-blank.
     *
     * @param scopeTagName scope tag name
     * @param scope        scope value
     * @param factory      factory method to accept the scope tag and the scope and return an instance of the implied type
     * @param <T>          type to which the scope tag and scope are converted
     * @return iterable of the scope if the scope tag name is non-null and non-blank; an empty iterable otherwise
     */
    static <T> Iterable<T> scopeIterable(String scopeTagName, String scope, BiFunction<String, String, T> factory) {
        return scopeTagName != null && !scopeTagName.isBlank() && scope != null
                ? List.of(factory.apply(scopeTagName, scope))
                : List.of();
    }

    @Override
    public Iterable<Tag> allTags(Meter.Id meterId, String scope) {
        return new MultiIterable<>(meterId.tags(),
                                   systemTags,
                                   scopeIterable(scope, Tag::create));
    }

    @Override
    public Iterable<Tag> allTags(Meter.Id meterId) {
        return new MultiIterable<>(meterId.tags(),
                                   systemTags);
    }

    @Override
    public Iterable<Map.Entry<String, String>> allTags(Iterable<Map.Entry<String, String>> explicitTags, String scope) {
        return new MultiIterable<>(explicitTags, scopeIterable(scope, AbstractMap.SimpleEntry::new));
    }

    @Override
    public void assignScope(String validScope, BiConsumer<String, String> tagSetter) {
        if (scopeTagName != null) {
            tagSetter.accept(scopeTagName, validScope);
        }
    }

    @Override
    public Optional<String> effectiveScope(Optional<String> candidateScope) {
        return candidateScope.isEmpty() && defaultScopeValue == null
                ? Optional.empty()
                : Optional.of(candidateScope.orElse(defaultScopeValue));
    }

    @Override
    public Optional<String> scopeTagName() {
        return Optional.ofNullable(scopeTagName);
    }

    private <T> Iterable<T> scopeIterable(String scope, BiFunction<String, String, T> factory) {
        return scopeIterable(scopeTagName, scope, factory);
    }

    static class MultiIterable<T> implements Iterable<T> {

        private final Iterable<T>[] iterables;

        private MultiIterable(Iterable<T>... iterables) {
            if (iterables.length == 0) {
                throw new IllegalArgumentException("Must provide at least one Iterable");
            }
            this.iterables = iterables;
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {

                private int nextIndex = 0;
                private Iterator<T> current = nextIterator();

                @Override
                public boolean hasNext() {
                    if (current.hasNext()) {
                        return true;
                    }

                    current = nextIterator();
                    return current.hasNext();
                }

                @Override
                public T next() {
                    return current.next();
                }

                private Iterator<T> nextIterator() {
                    while (nextIndex < iterables.length) {
                        Iterator<T> candidateNextIterator = iterables[nextIndex].iterator();
                        if (candidateNextIterator.hasNext()) {
                            nextIndex++;
                            return candidateNextIterator;
                        }
                        nextIndex++;
                    }
                    return Collections.emptyIterator();
                }
            };
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            for (Iterable<T> it : iterables) {
                it.forEach(action);
            }
        }
    }

}
