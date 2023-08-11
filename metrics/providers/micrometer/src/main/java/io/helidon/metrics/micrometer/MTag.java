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
import java.util.Objects;
import java.util.StringJoiner;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

class MTag implements io.helidon.metrics.api.Tag {

    /**
     * Adapts an {@link java.lang.Iterable} of Micrometer tag to an iterable of Helidon tag.
     *
     * @param tags Micrometer tags
     * @return Helidon tags
     */
    static Iterable<io.helidon.metrics.api.Tag> neutralTags(Iterable<Tag> tags) {
        return () -> new Iterator<>() {

            private final Iterator<Tag> iter = tags.iterator();

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public io.helidon.metrics.api.Tag next() {
                return MTag.create(iter.next());
            }
        };
    }

    static Tags mTags(Iterable<io.helidon.metrics.api.Tag> tags) {
        return Tags.of(tags(tags));
    }

    static Iterable<Tag> tags(Iterable<io.helidon.metrics.api.Tag> tags) {
        return () -> new Iterator<>() {

            private final Iterator<io.helidon.metrics.api.Tag> iter = tags.iterator();

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public Tag next() {
                io.helidon.metrics.api.Tag next = iter.next();
                return Tag.of(next.key(), next.value());
            }
        };
    }

    /**
     * Adapts a Micrometer tag to a Helidon tag.
     *
     * @param tag Micrometer tag
     * @return Helidon tag
     */
    static MTag create(Tag tag) {
        return new MTag(tag);
    }

    static MTag of(String key, String value) {
        return MTag.create(Tag.of(key, value));
    }

    private final Tag delegate;

    private MTag(Tag delegate) {
        this.delegate = delegate;
    }

    @Override
    public String key() {
        return delegate.getKey();
    }

    @Override
    public String value() {
        return delegate.getValue();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", MTag.class.getSimpleName() + "[", "]")
                .add(key() + "=" + value())
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MTag mTag = (MTag) o;
        return Objects.equals(delegate, mTag.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate.hashCode());
    }
}
