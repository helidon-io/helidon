/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.metrics;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import io.helidon.metrics.api.AbstractMetric;
import io.helidon.metrics.api.SystemTagsManager;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Base for our implementations of various metrics.
 */
abstract class MetricImpl extends AbstractMetric implements HelidonMetric {
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


    protected static Tags allTags(String scope, Tag[] tags) {
        return toTags(SystemTagsManager.instance().allTags(iterable(tags), scope));
    }

    private static Tags toTags(Iterable<Map.Entry<String, String>> iterable) {
        return Tags.of(tags(iterable));
    }

    private static Iterable<io.micrometer.core.instrument.Tag> tags(Iterable<Map.Entry<String, String>> iterable) {
        return new Iterable<>() {

            private final Iterator<Map.Entry<String, String>> iterator = iterable.iterator();

            @Override
            public Iterator<io.micrometer.core.instrument.Tag> iterator() {
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public io.micrometer.core.instrument.Tag next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        var next = iterator.next();
                        return io.micrometer.core.instrument.Tag.of(next.getKey(), next.getValue());
                    }
                };
            }
        };
    }

    protected static String sanitizeUnit(String unit) {
        return unit != null && !unit.equals(MetricUnits.NONE)
                ? unit
                : null;
    }

    private static Iterable<Map.Entry<String, String>> iterable(Tag... tags) {
        return () -> new Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() {
                return next < tags.length;
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
