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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
//import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inspired by the Spring Data's {@code Page} and GORM's {@code PagedResultList}, this models a type that supports
 * pagination operations.
 *
 * <p>A Page is a result set associated with a particular {@link Pageable} that includes a calculation of the total
 * size of number of records.</p>
 *
 * @param <T> The generic type
 * @author graemerocher
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
//@JsonDeserialize(as = DefaultPage.class)
public interface Page<T> extends Slice<T> {

    Page<?> EMPTY = new DefaultPage<>(Collections.emptyList(), Pageable.unpaged(), 0);

    /**
     * @return The total size of the all records.
     */
    long getTotalSize();

    /**
     * @return The total number of pages
     */
    default int getTotalPages() {
        int size = getSize();
        return size == 0 ? 1 : (int) Math.ceil((double) getTotalSize() / (double) size);
    }

    /**
     * Maps the content with the given function.
     *
     * @param function The function to apply to each element in the content.
     * @param <T2> The type returned by the function
     * @return A new slice with the mapped content
     */
    @Override
    default <T2> Page<T2> map(Function<T, T2> function) {
        List<T2> content = getContent().stream().map(function).collect(Collectors.toList());
        return new DefaultPage<>(content, getPageable(), getTotalSize());
    }

    /**
     * Creates a slice from the given content and pageable.
     * @param content The content
     * @param pageable The pageable
     * @param totalSize The total size
     * @param <T> The generic type
     * @return The slice
     */
    @JsonCreator
    static <T> Page<T> of(
            @JsonProperty("content") List<T> content,
            @JsonProperty("pageable") Pageable pageable,
            @JsonProperty("totalSize") long totalSize) {
        return new DefaultPage<>(content, pageable, totalSize);
    }

    /**
     * Creates an empty page object.
     * @param <T2> The generic type
     * @return The slice
     */
    @SuppressWarnings("unchecked")
    static <T2> Page<T2> empty() {
        return (Page<T2>) EMPTY;
    }
}
