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

import io.micrometer.core.instrument.Tag;

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
                return MTag.of(iter.next());
            }
        };
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
    static MTag of(Tag tag) {
        return new MTag(tag);
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
}
