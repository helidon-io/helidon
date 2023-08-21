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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

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

    private final List<Tag> systemTags;
    private final String scopeTagName;

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

    static SystemTagsManagerImpl createWithoutSaving(MetricsConfig metricsConfig) {
        return new SystemTagsManagerImpl(metricsConfig);
    }

    private SystemTagsManagerImpl(MetricsConfig metricsConfig) {
        List<Tag> result = new ArrayList<>(metricsConfig.globalTags());

        String appTagName = MetricsProgrammaticSettings.instance().appTagName();
        if (metricsConfig.appName().isPresent() && appTagName != null && !appTagName.isBlank()) {
            result.add(Tag.create(appTagName, metricsConfig.appName().get()));
        }
        systemTags = Collections.unmodifiableList(result);
        scopeTagName = MetricsProgrammaticSettings.instance().scopeTagName();
    }

    // for testing
    private SystemTagsManagerImpl() {
        systemTags = Collections.emptyList();
        scopeTagName = "_testScope_";
    }

    @Override
    public Iterable<Tag> allTags(Meter.Id meterId, String scope) {
        return new MultiIterable<>(meterId.tags(),
                                   systemTags,
                                   scopeIterable(scope));
    }

    @Override
    public Iterable<Tag> allTags(Meter.Id meterId) {
        return new MultiIterable<>(meterId.tags(),
                                   systemTags);
    }

    private Iterable<Tag> scopeIterable(String scope) {
        return () -> new Iterator<>() {

            private boolean hasNext = scopeTagName != null && !scopeTagName.isBlank() && scope != null;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public Tag next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                hasNext = false;
                return Tag.create(scopeTagName, scope);
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
