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
package io.helidon.data;

import java.util.List;

/**
 * Pageable query result as pages with total size of the result.
 * <p>
 * Page number and number of records returned on each page depends on values of the
 * {@link #request()} used to return current page.
 * <p>
 * Returned value of {@link #totalSize()} and {@link #totalPages()} requires another query
 * to be executed while reading data from the database, so retrieving result as {@link Slice}
 * should be faster than using {@link Page}.
 *
 * @param <T> query result type (entity or entity attribute)
 */
public interface Page<T> extends Slice<T> {

    /**
     * Create pageable query result as page with total size of the result.
     *
     * @param request   pageable query result request
     * @param content   page content as {@link java.util.List}
     * @param totalSize total size of the result across all pages
     * @param <T>       query result type (entity or entity attribute)
     * @return new instance of the query result
     */
    static <T> Page<T> create(PageRequest request, List<T> content, int totalSize) {
        return new PageImpl<>(request, content, totalSize);
    }

    /**
     * Total size of the result across all pages.
     *
     * @return total size of the result
     */
    int totalSize();

    /**
     * Total number of pages.
     *
     * @return total number of pages
     */
    int totalPages();

}
