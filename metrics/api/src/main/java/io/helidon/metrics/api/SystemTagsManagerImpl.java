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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Captures and makes available for output any system tag settings to be applied when metric IDs are output.
 * <P>
 *     In MP, the config might contain the config key {@code mp.metrics.tags}. In SE, the config might be {@code metrics.tags},
 *     either of which can be a string of the form {@code tag1=value1,tag2=value2,...}.
 * </P>
 * <p>
 *     Further, the MP config key {@code mp.metrics.appName} or the SE config key {metrics.appName} can convey
 *     an application name which will add a tag conveying the app name to each metric ID written to output.
 * </p>
 */
class SystemTagsManagerImpl implements SystemTagsManager {

    private static SystemTagsManagerImpl instance = new SystemTagsManagerImpl();

    private final Map<String, String> systemTags;

    private final String scopeTagName;

    /**
     * Returns the singleton instance of the system tags manager.
     *
     * @return the singleton instance
     */
    public static SystemTagsManager instance() {
        return instance;
    }

    static SystemTagsManagerImpl create(MetricsSettings metricsSettings) {
        instance = createWithoutSaving(metricsSettings);
        return instance;
    }

    static SystemTagsManagerImpl createWithoutSaving(MetricsSettings metricsSettings) {
        return new SystemTagsManagerImpl(metricsSettings);
    }

    private SystemTagsManagerImpl(MetricsSettings metricsSettings) {
        Map<String, String> result = new HashMap<>(metricsSettings.globalTags());
        String appTagName = MetricsProgrammaticSettings.instance().appTagName();
        if (metricsSettings.appTagValue() != null && appTagName != null && !appTagName.isBlank()) {
            result.put(appTagName, metricsSettings.appTagValue());
        }
        systemTags = Collections.unmodifiableMap(result);
        scopeTagName = MetricsProgrammaticSettings.instance().scopeTagName();
    }

    // for testing
    private SystemTagsManagerImpl() {
        systemTags = Collections.emptyMap();
        scopeTagName = "_testScope_";
    }

    @Override
    public Iterable<Map.Entry<String, String>> allTags(Map<String, String> explicitTags, String scope) {
        return new MultiIterable<>(explicitTags.entrySet(),
                                   systemTags.entrySet(),
                                   scopeIterable(scope));
    }

    @Override
    public Iterable<Map.Entry<String, String>> allTags(MetricID metricID, String scope) {
        return new MultiIterable<>(metricID.getTags().entrySet(),
                                   systemTags.entrySet(),
                                   scopeIterable(scope));
    }

    @Override
    public Iterable<Map.Entry<String, String>> allTags(Iterable<Map.Entry<String, String>> explicitTags, String scope) {
        return new MultiIterable<>(explicitTags,
                                   systemTags.entrySet(),
                                   scopeIterable(scope));
    }

    @Override
    public Iterable<Map.Entry<String, String>> allTags(Iterable<Map.Entry<String, String>> explicitTags) {
        return new MultiIterable<>(explicitTags,
                                   systemTags.entrySet());
    }

    @Override
    public Iterable<Map.Entry<String, String>> allTags(MetricID metricId) {
        return new MultiIterable<>(metricId.getTags().entrySet(),
                                   systemTags.entrySet());
    }

    @Override
    public MetricID metricIdWithAllTags(MetricID original, String scope) {
        List<Tag> tags = new ArrayList<>();
        List.of(original.getTags().entrySet(),
                systemTags.entrySet(),
                scopeIterable(scope))
                .forEach(iter -> iter.forEach(entry -> tags.add(new Tag(entry.getKey(), entry.getValue()))));
        return new MetricID(original.getName(), tags.toArray(new Tag[0]));
    }

    private Iterable<Map.Entry<String, String>> scopeIterable(String scope) {
        return () -> new Iterator<>() {

            private boolean hasNext = scopeTagName != null && !scopeTagName.isBlank() && scope != null;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public Map.Entry<String, String> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                hasNext = false;
                return new AbstractMap.SimpleImmutableEntry<>(scopeTagName, scope);
            }
        };
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
