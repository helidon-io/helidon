/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class LazyListImpl<T> implements LazyList<T> {

    private final List<LazyValue<T>> lazyValues = new ArrayList<>();
    private List<T> allLoaded;

    LazyListImpl(List<LazyValue<T>> lazyValues) {
        this.lazyValues.addAll(lazyValues);
    }

    @Override
    public void add(final Supplier<T> supplier) {
        lazyValues.add(LazyValue.create(supplier));
    }

    private List<T> loadAll() {
        if (allLoaded == null) {
            allLoaded = lazyValues.stream().map(LazyValue::get).collect(Collectors.toList());
        }
        return allLoaded;
    }

    @Override
    public int size() {
        return lazyValues.size();
    }

    @Override
    public boolean isEmpty() {
        return lazyValues.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return lazyValues.stream()
                .map(LazyValue::get)
                .anyMatch(tLazyValue -> Objects.equals(o, tLazyValue));
    }

    @Override
    public Iterator<T> iterator() {
        Iterator<LazyValue<T>> lazyValueIterator = lazyValues.iterator();
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return lazyValueIterator.hasNext();
            }

            @Override
            public T next() {
                return lazyValueIterator.next().get();
            }
        };
    }

    @Override
    public Object[] toArray() {
        return loadAll().toArray();
    }

    @Override
    public <T1> T1[] toArray(final T1[] a) {
        return loadAll().toArray(a);
    }

    @Override
    public boolean add(final T t) {
        return lazyValues.add(LazyValue.create(t));
    }

    @Override
    public boolean remove(final Object o) {
        return lazyValues.removeIf(lv -> Objects.equals(o, lv.get()));
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return loadAll().containsAll(c);
    }

    @Override
    public boolean addAll(final Collection<? extends T> c) {
        c.forEach(v -> lazyValues.add(LazyValue.create(v)));
        return !c.isEmpty();
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends T> c) {
        c.forEach(this::add);
        return !c.isEmpty();
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return loadAll().removeAll(c);
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        return loadAll().retainAll(c);
    }

    @Override
    public void clear() {
        lazyValues.clear();
        if (allLoaded != null) {
            allLoaded.clear();
        }
    }

    @Override
    public T get(final int index) {
        return lazyValues.get(index).get();
    }

    @Override
    public T set(final int index, final T element) {
        return lazyValues.set(index, LazyValue.create(element)).get();
    }

    @Override
    public void add(final int index, final T element) {
        lazyValues.add(index, LazyValue.create(element));
    }

    @Override
    public T remove(final int index) {
        return lazyValues.remove(index).get();
    }

    @Override
    public int indexOf(final Object o) {
        for (int i = 0; i < lazyValues.size(); i++) {
            LazyValue<T> lv = lazyValues.get(i);
            if (Objects.equals(o, lv.get())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(final Object o) {
        int index = -1;
        for (int i = 0; i < lazyValues.size(); i++) {
            LazyValue<T> lv = lazyValues.get(i);
            if (Objects.equals(o, lv.get())) {
                index = i;
            }
        }
        return index;
    }

    @Override
    public ListIterator<T> listIterator() {
        return new LazyListIterator<>(lazyValues.listIterator());
    }

    @Override
    public ListIterator<T> listIterator(final int index) {
        return new LazyListIterator<>(lazyValues.listIterator(index));
    }

    @Override
    public List<T> subList(final int fromIndex, final int toIndex) {
        return new LazyListImpl<>(lazyValues.subList(fromIndex, toIndex));
    }

    private static class LazyListIterator<T> implements ListIterator<T> {

        private final ListIterator<LazyValue<T>> innerIterator;

        LazyListIterator(ListIterator<LazyValue<T>> innerIterator) {
            this.innerIterator = innerIterator;
        }

        @Override
        public boolean hasNext() {
            return this.innerIterator.hasNext();
        }

        @Override
        public T next() {
            return this.innerIterator.next().get();
        }

        @Override
        public boolean hasPrevious() {
            return this.innerIterator.hasPrevious();
        }

        @Override
        public T previous() {
            return this.innerIterator.previous().get();
        }

        @Override
        public int nextIndex() {
            return this.innerIterator.nextIndex();
        }

        @Override
        public int previousIndex() {
            return this.innerIterator.previousIndex();
        }

        @Override
        public void remove() {
            this.innerIterator.remove();
        }

        @Override
        public void set(final T t) {
            this.innerIterator.set(LazyValue.create(t));
        }

        @Override
        public void add(final T t) {
            this.innerIterator.add(LazyValue.create(t));
        }
    }
}
