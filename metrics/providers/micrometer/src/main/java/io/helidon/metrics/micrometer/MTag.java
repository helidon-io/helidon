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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import io.micrometer.core.instrument.Tag;

class MTag implements io.helidon.metrics.api.Tag {

    private final Tag delegate;

    private MTag(Tag delegate) {
        this.delegate = delegate;
    }


    /**
     * Adapts an {@link java.lang.Iterable} of Micrometer tag to an iterable of Helidon tag.
     *
     * @param tags Micrometer tags
     * @return Helidon tags
     */
    static Iterable<io.helidon.metrics.api.Tag> neutralTags(Iterable<Tag> tags) {
        List<io.helidon.metrics.api.Tag> result = new ArrayList<>();
        tags.forEach(mmTag -> result.add(MTag.create(mmTag)));
        return result;
    }

    static Iterable<Tag> tags(Iterable<io.helidon.metrics.api.Tag> tags) {
        List<Tag> result = new ArrayList<>();
        tags.forEach(neutralTag -> result.add(Tag.of(neutralTag.key(), neutralTag.value())));
        return result;
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
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
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

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(delegate);
    }
}
