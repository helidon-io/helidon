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

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Query result as page without total size of the result.
 * @param <T> query result type (entity or entity attribute)
 */
class SliceImpl<T> implements Slice<T> {

    private final PageRequest request;
    private final List<T> content;

    SliceImpl(PageRequest request, List<T> content) {
        this.request = request;
        this.content = content;
    }

    @Override
    public Iterator<T> iterator() {
        return content.iterator();
    }

    @Override
    public Stream<T> stream() {
        return content.stream();
    }

    @Override
    public List<T> list() {
        return content;
    }

    @Override
    public PageRequest request() {
        return request;
    }

}
