/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.SystemTagsManager;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Base for our implementations of various metrics.
 */
abstract class MetricImpl<M extends Meter> extends AbstractMetric<M> implements HelidonMetric<M> {
    static final double[] DEFAULT_PERCENTILES = {0.5, 0.75, 0.95, 0.98, 0.99, 0.999};
    static final int DEFAULT_PERCENTILE_PRECISION = 3;

    MetricImpl(String registryType, Metadata metadata) {
        super(registryType, metadata);
    }


    @Override
    public String getName() {
        return metadata().getName();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{"
                + "registryType='" + registryType() + '\''
                + ", metadata=" + metadata()
                + toStringDetails()
                + '}';
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    protected String toStringDetails() {
        return "";
    }

    @Override
    public boolean removeViaDelegate(MeterRegistry meterRegistry) {
        return delegate() != null && meterRegistry.remove(delegate()).isPresent();
    }

    /**
     * Returns an iterable of Helidon {@link io.helidon.metrics.api.Tag} including global tags, any app tag, and a scope
     * tag (if metrics is so configured to add a scope tag).
     *
     * @param scope scope of the meter
     * @param tags explicitly-defined tags from the application code
     * @return iterable ot Helidon tags
     */
    protected static Iterable<io.helidon.metrics.api.Tag> allTags(String scope, Tag[] tags) {
        return toHelidonTags(SystemTagsManager.instance().withScopeTag(iterableEntries(tags), scope));
    }

    static String resolvedScope(Meter delegate) {
        return SystemTagsManager.instance().effectiveScope(delegate.scope()).orElse(Meter.Scope.DEFAULT);
    }

    /**
     * Converts an iterable of map entries (representing tag names and values) into an iterable of Helidon tags.
     *
     * @param entriesIterable iterable of map entries
     * @return iterable of {@link io.helidon.metrics.api.Tag}
     */
    private static Iterable<io.helidon.metrics.api.Tag> toHelidonTags(Iterable<Map.Entry<String, String>> entriesIterable) {
        return () -> new Iterator<>() {

            private final Iterator<Map.Entry<String, String>> entriesIterator = entriesIterable.iterator();

            @Override
            public boolean hasNext() {
                return entriesIterator.hasNext();
            }

            @Override
            public io.helidon.metrics.api.Tag next() {
                if (!entriesIterator.hasNext()) {
                    throw new NoSuchElementException();
                }
                var entry = entriesIterator.next();
                return io.helidon.metrics.api.Tag.create(entry.getKey(), entry.getValue());
            }
        };
    }

    protected static String sanitizeUnit(String unit) {
        return unit != null && !unit.equals(MetricUnits.NONE)
                ? unit
                : null;
    }

    private static Iterable<Map.Entry<String, String>> iterableEntries(Tag... tags) {
        return () -> new Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() {
                return tags != null && next < tags.length;
            }

            @Override
            public Map.Entry<String, String> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                var result = new AbstractMap.SimpleEntry<>(tags[next].getTagName(),
                                                           tags[next].getTagValue());
                next++;
                return result;
            }
        };
    }

}
