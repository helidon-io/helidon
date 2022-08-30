/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link Page}.
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <T> The generic type
 */
class DefaultPage<T> extends DefaultSlice<T> implements Page<T> {

    private final long totalSize;

    /**
     * Default constructor.
     * @param content The content
     * @param pageable The pageable
     * @param totalSize The total size
     */
    @JsonCreator
    DefaultPage(
            @JsonProperty("content")
            List<T> content,
            @JsonProperty("pageable")
            Pageable pageable,
            @JsonProperty("totalSize")
            long totalSize) {
        super(content, pageable);
        this.totalSize = totalSize;
    }

    @Override
    public long getTotalSize() {
        return totalSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultPage)) {
            return false;
        }
        DefaultPage<?> that = (DefaultPage<?>) o;
        return totalSize == that.totalSize && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalSize, super.hashCode());
    }

    @Override
    public String toString() {
        return "DefaultPage{" +
                "totalSize=" + totalSize +
                ",content=" + getContent() +
                ",pageable=" + getPageable() +
                '}';
    }
}
