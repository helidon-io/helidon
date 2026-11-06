/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.api;

import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.mapper.Value;
import io.helidon.common.uri.UriQuery;
import io.helidon.common.uri.UriQueryWriteable;

// Preserve an imported query until its parameters are explicitly changed.
final class ClientUriQuery implements UriQueryWriteable {
    private final UriQueryWriteable delegate = UriQueryWriteable.create();

    private String rawQuery;

    ClientUriQuery() {
    }

    ClientUriQuery(UriQuery source) {
        replace(source);
    }

    @Override
    public boolean equals(Object other) {
        return delegate.equals(other);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public String rawValue() {
        return rawQuery == null ? delegate.rawValue() : rawQuery;
    }

    @Override
    public String value() {
        return delegate.value();
    }

    @Override
    public String getRaw(String name) {
        return delegate.getRaw(name);
    }

    @Override
    public List<String> getAllRaw(String name) {
        List<String> values = delegate.getAllRaw(name);
        return new QueryList(values, () -> invalidate(name, values, true));
    }

    @Override
    public List<String> all(String name) {
        List<String> values = delegate.all(name);
        return new QueryList(values, () -> invalidate(name, values, false));
    }

    @Override
    public List<Value<String>> allValues(String name) {
        return delegate.allValues(name);
    }

    @Override
    public String get(String name) {
        return delegate.get(name);
    }

    @Override
    public OptionalValue<String> first(String name) {
        return delegate.first(name);
    }

    @Override
    public boolean contains(String name) {
        return delegate.contains(name);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public Set<String> names() {
        return new AbstractSet<>() {
            @Override
            public Iterator<String> iterator() {
                Iterator<String> iterator = delegate.names().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public String next() {
                        return iterator.next();
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                        rawQuery = null;
                    }
                };
            }

            @Override
            public int size() {
                return delegate.names().size();
            }
        };
    }

    @Override
    public String component() {
        return delegate.component();
    }

    @Override
    public UriQueryWriteable from(UriQuery source) {
        rawQuery = null;
        delegate.from(source instanceof ClientUriQuery query ? query.delegate : source);
        return this;
    }

    @Override
    public UriQueryWriteable set(String name, String... values) {
        rawQuery = null;
        delegate.set(name, values);
        return this;
    }

    @Override
    public UriQueryWriteable add(String name, String value) {
        rawQuery = null;
        delegate.add(name, value);
        return this;
    }

    @Override
    public UriQueryWriteable setIfAbsent(String name, String... values) {
        if (!delegate.contains(name)) {
            rawQuery = null;
            delegate.setIfAbsent(name, values);
        }
        return this;
    }

    @Override
    public UriQueryWriteable remove(String name) {
        rawQuery = null;
        delegate.remove(name);
        return this;
    }

    @Override
    public UriQueryWriteable remove(String name, Consumer<List<String>> removedConsumer) {
        rawQuery = null;
        delegate.remove(name, removedConsumer);
        return this;
    }

    @Override
    public void fromQueryString(String queryString) {
        rawQuery = null;
        delegate.fromQueryString(queryString);
    }

    @Override
    public void clear() {
        rawQuery = null;
        delegate.clear();
    }

    void appendRawQuery(String queryString) {
        if (queryString.isEmpty()) {
            return;
        }
        String previous = rawValue();
        // URI query imports have historically interpreted '+' as a space in parameter lookups.
        delegate.fromQueryString(queryString.replace("+", "%20"));
        rawQuery = previous.isEmpty() ? queryString : previous + '&' + queryString;
    }

    void replace(UriQuery source) {
        if (source == this) {
            return;
        }
        String raw = source.rawValue();
        rawQuery = null;
        delegate.clear();
        if (source instanceof ClientUriQuery query) {
            delegate.from(query.delegate);
            rawQuery = raw;
        } else {
            delegate.from(source);
            if (matchesRawQuery(raw)) {
                rawQuery = raw;
            }
        }
    }

    private boolean matchesRawQuery(String raw) {
        if (raw.isEmpty()) {
            return delegate.rawValue().isEmpty();
        }
        Map<String, Integer> offsets = new HashMap<>();
        int start = 0;
        while (true) {
            int and = raw.indexOf('&', start);
            int end = and == -1 ? raw.length() : and;
            int eq = raw.indexOf('=', start, end);
            boolean hasValue = eq != -1;
            String name = raw.substring(start, hasValue ? eq : end);
            if (!delegate.contains(name)) {
                return false;
            }
            int offset = offsets.getOrDefault(name, 0);
            if (hasValue) {
                List<String> values = delegate.getAllRaw(name);
                if (offset >= values.size()) {
                    return false;
                }
                String value = values.get(offset);
                if (value.length() != end - eq - 1 || !raw.regionMatches(eq + 1, value, 0, value.length())) {
                    return false;
                }
                offset++;
            }
            // Bare flags have no values in UriQuery's parsed raw lists.
            offsets.put(name, offset);
            if (and == -1) {
                break;
            }
            start = and + 1;
        }
        for (Map.Entry<String, Integer> entry : offsets.entrySet()) {
            if (delegate.getAllRaw(entry.getKey()).size() != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void invalidate(String name, List<String> values, boolean raw) {
        // Lists removed or replaced in the delegate remain mutable, but no longer affect this query.
        boolean present = raw ? delegate.contains(name) : delegate.names().contains(name);
        if (present && (raw ? delegate.getAllRaw(name) : delegate.all(name)) == values) {
            rawQuery = null;
        }
    }

    private static final class QueryList extends AbstractList<String> implements RandomAccess {
        private final List<String> delegate;
        private final Runnable invalidate;

        private QueryList(List<String> delegate, Runnable invalidate) {
            this.delegate = delegate;
            this.invalidate = invalidate;
        }

        @Override
        public String get(int index) {
            return delegate.get(index);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public String set(int index, String element) {
            String previous = delegate.set(index, element);
            invalidate.run();
            return previous;
        }

        @Override
        public void add(int index, String element) {
            delegate.add(index, element);
            invalidate.run();
        }

        @Override
        public String remove(int index) {
            String previous = delegate.remove(index);
            invalidate.run();
            return previous;
        }

        @Override
        public boolean addAll(Collection<? extends String> values) {
            boolean changed = delegate.addAll(values);
            if (changed) {
                invalidate.run();
            }
            return changed;
        }

        @Override
        public boolean addAll(int index, Collection<? extends String> values) {
            boolean changed = delegate.addAll(index, values);
            if (changed) {
                invalidate.run();
            }
            return changed;
        }

        @Override
        public boolean removeAll(Collection<?> values) {
            try {
                return delegate.removeAll(values);
            } finally {
                invalidate.run();
            }
        }

        @Override
        public boolean retainAll(Collection<?> values) {
            try {
                return delegate.retainAll(values);
            } finally {
                invalidate.run();
            }
        }

        @Override
        public boolean removeIf(Predicate<? super String> filter) {
            boolean changed = delegate.removeIf(filter);
            if (changed) {
                invalidate.run();
            }
            return changed;
        }

        @Override
        public void replaceAll(UnaryOperator<String> operator) {
            try {
                delegate.replaceAll(operator);
            } finally {
                invalidate.run();
            }
        }

        @Override
        public void sort(Comparator<? super String> comparator) {
            try {
                delegate.sort(comparator);
            } finally {
                invalidate.run();
            }
        }

        @Override
        public void clear() {
            delegate.clear();
            invalidate.run();
        }

        @Override
        public Iterator<String> iterator() {
            return listIterator();
        }

        @Override
        public ListIterator<String> listIterator(int index) {
            ListIterator<String> iterator = delegate.listIterator(index);
            return new ListIterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public String next() {
                    return iterator.next();
                }

                @Override
                public boolean hasPrevious() {
                    return iterator.hasPrevious();
                }

                @Override
                public String previous() {
                    return iterator.previous();
                }

                @Override
                public int nextIndex() {
                    return iterator.nextIndex();
                }

                @Override
                public int previousIndex() {
                    return iterator.previousIndex();
                }

                @Override
                public void remove() {
                    iterator.remove();
                    invalidate.run();
                }

                @Override
                public void set(String value) {
                    iterator.set(value);
                    invalidate.run();
                }

                @Override
                public void add(String value) {
                    iterator.add(value);
                    invalidate.run();
                }
            };
        }

        @Override
        public List<String> subList(int fromIndex, int toIndex) {
            return new QueryList(delegate.subList(fromIndex, toIndex), invalidate);
        }
    }
}
