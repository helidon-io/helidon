/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.micrometer;

import java.util.Iterator;
import java.util.stream.Stream;

import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Tags;

class MicrometerTags implements Tags {

    private final io.micrometer.core.instrument.Tags delegate;

    static Tags of(String key, String value) {
        return new MicrometerTags(io.micrometer.core.instrument.Tags.of(key, value));
    }

    static Tags concat(Iterable<? extends Tag> tags, Iterable<? extends Tag> other) {
        return of(tags).and(other);
    }

    static Tags concat(Iterable<? extends Tag> tags, String... keyValues) {
        return of(tags).and(keyValues);
    }

    static Tags of(Iterable<? extends Tag> tags) {
        return new MicrometerTags(io.micrometer.core.instrument.Tags.of(toMTags(tags)));
    }

    static Tags of(String... keyValues) {
        return new MicrometerTags(io.micrometer.core.instrument.Tags.of(keyValues));
    }

    static Tags of(Tag... tags) {
        return new MicrometerTags(io.micrometer.core.instrument.Tags.of(toMTags(tags)));
    }

    static Tags empty() {
        return new MicrometerTags(io.micrometer.core.instrument.Tags.empty());
    }

    private static Iterable<io.micrometer.core.instrument.Tag> toMTags(Iterable<? extends Tag> tags) {
        return new Iterable<>() {

            private final Iterator<? extends Tag> tagIt = tags.iterator();

            @Override
            public Iterator<io.micrometer.core.instrument.Tag> iterator() {
                return new Iterator<io.micrometer.core.instrument.Tag>() {
                    @Override
                    public boolean hasNext() {
                        return tagIt.hasNext();
                    }

                    @Override
                    public io.micrometer.core.instrument.Tag next() {
                        Tag next = tagIt.next();
                        return io.micrometer.core.instrument.Tag.of(next.key(), next.value());
                    }
                };
            }
        };
    }

    private static io.micrometer.core.instrument.Tag[] toMTags(Tag[] tags) {
        io.micrometer.core.instrument.Tag[] result = new io.micrometer.core.instrument.Tag[tags.length];
        for (int i = 0; i < tags.length; i++) {
            result[i] = io.micrometer.core.instrument.Tag.of(tags[i].key(), tags[i].value());
        }
        return result;
    }

    private MicrometerTags(io.micrometer.core.instrument.Tags delegate) {
        this.delegate = delegate;
    }

    public Tags and(String key, String value) {
        return new MicrometerTags(delegate.and(key, value));
    }

    public Tags and(String... keyValues) {
        return new MicrometerTags(delegate.and(keyValues));
    }

    public Tags and(Tag... tags) {
        return new MicrometerTags(delegate.and(toMTags(tags)));
    }

    public Tags and(Iterable<? extends Tag> tags) {
        return new MicrometerTags(delegate.and(toMTags(tags)));
    }

    public Iterator<Tag> iterator() {
        return new Iterator<>() {
            private final Iterator<? extends io.micrometer.core.instrument.Tag> mTagIt = delegate.iterator();

            @Override
            public boolean hasNext() {
                return mTagIt.hasNext();
            }

            @Override
            public Tag next() {
                var next = mTagIt.next();
                return MicrometerTag.of(next.getKey(), next.getValue());
            }
        };
    }

    @Override
    public Stream<Tag> stream() {
        return delegate.stream()
                .map(MicrometerTag::of);
    }
}
