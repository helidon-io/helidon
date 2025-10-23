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

package io.helidon.service.jndi;

import java.util.Iterator;
import java.util.List;

import javax.naming.NamingEnumeration;

class NamingEnum<T> implements NamingEnumeration<T> {
    private final Iterator<T> iterator;

    NamingEnum(List<T> values) {
        this.iterator = values.iterator();
    }

    @Override
    public Iterator<T> asIterator() {
        return iterator;
    }

    @Override
    public T nextElement() {
        return iterator.next();
    }

    @Override
    public boolean hasMoreElements() {
        return iterator.hasNext();
    }

    @Override
    public void close() {

    }

    @Override
    public boolean hasMore() {
        return iterator.hasNext();
    }

    @Override
    public T next() {
        return iterator.next();
    }
}
