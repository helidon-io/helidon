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
 * Query result as page with total size of the result.
 *
 * @param <T> query result type (entity or entity attribute)
 */
class PageImpl<T> extends SliceImpl<T> implements Page<T> {

    private final int totalSize;

    PageImpl(PageRequest request, List<T> content, int totalSize) {
        super(request, content);
        this.totalSize = totalSize;
    }

    @Override
    public int totalSize() {
        return totalSize;
    }

    @Override
    public int totalPages() {
        int size = request().size();
        return size == 0 ? 1 : (int) Math.ceil((double) totalSize() / (double) size);
    }

}
