/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.api;

import java.util.List;
import java.util.stream.Stream;

/**
 * Query result as page without total size of the result.
 *
 * @param <T> query result type (entity or entity attribute)
 */
public interface Slice<T> extends Iterable<T> {

    /**
     * Create query result as page without total size of the result.
     *
     * @param request pageable query result request
     * @param content page content as {@link List<T>}
     * @param <T> query result type (entity or entity attribute)
     * @return new instance of the query result
     *
     */
    static <T> Slice<T> create(PageRequest request, List<T> content) {
        return new SliceImpl<>(request, content);
    }

    /**
     * Current page content as {@link java.util.stream.Stream} of query result type {@link T}.
     * Never returns {@code null}.
     *
     * @return current page content as {@link java.util.stream.Stream}
     */
    Stream<T> stream();

    /**
     * Current page content as {@link java.util.List} of query result type {@link T}.
     * Never returns {@code null}.
     *
     * @return current page content as {@link Stream}
     */
    List<T> list();

    /**
     * Query page request of current page.
     * Never returns {@code null}.
     *
     * @return current page request
     */
    PageRequest request();

}
