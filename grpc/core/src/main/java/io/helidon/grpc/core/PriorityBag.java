/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.annotation.Priority;

/**
 * A bag of values ordered by priority.
 * <p>
 * Within a given priority value elements are ordered in the order that they
 * were added to the bag.
 *
 * @param <T> the type of elements in the bag
 */
public class PriorityBag<T> implements Iterable<T> {

    private final Map<Integer, List<T>> contents;

    private final List<T> noPriorityList;

    private final int defaultPriority;

    /**
     * Create a new {@link PriorityBag} where elements
     * added with no priority will be last in the order.
     */
    public PriorityBag() {
        this(new TreeMap<>(), new ArrayList<>(), -1);
    }

    /**
     * Create a new {@link PriorityBag} where elements
     * added with no priority will be be given a default
     * priority value.
     *
     * @param defaultPriority  the default priority value to assign
     *                         to elements added with no priority
     */
    public PriorityBag(int defaultPriority) {
        this(new TreeMap<>(), new ArrayList<>(), defaultPriority);
    }


    private PriorityBag(Map<Integer, List<T>> contents, List<T> noPriorityList, int defaultPriority) {
        this.contents = contents;
        this.noPriorityList = noPriorityList;
        this.defaultPriority = defaultPriority;
    }

    /**
     * Obtain a copy of this {@link PriorityBag}.
     *
     * @return a copy of this {@link PriorityBag}
     */
    public PriorityBag<T> copyMe() {
        PriorityBag<T> copy = new PriorityBag<>();
        copy.merge(this);
        return copy;
    }

    /**
     * Obtain an immutable copy of this {@link PriorityBag}.
     *
     * @return an immutable copy of this {@link PriorityBag}
     */
    public PriorityBag<T> readOnly() {
        return new PriorityBag<>(Collections.unmodifiableMap(contents),
                                 Collections.unmodifiableList(noPriorityList),
                                 defaultPriority);
    }

    /**
     * Merge a {@link PriorityBag} into this {@link PriorityBag}.
     *
     * @param bag  the bag to merge
     */
    public void merge(PriorityBag<? extends T> bag) {
        bag.contents.forEach((priority, value) -> addAll(value, priority));
        this.noPriorityList.addAll(bag.noPriorityList);
    }

    /**
     * Add elements to the bag.
     * <p>
     * If the element's class is annotated with the {@link javax.annotation.Priority}
     * annotation then that value will be used to determine priority otherwise the
     * default priority value will be used.
     *
     * @param values  the elements to add
     */
    public void addAll(Iterable<? extends T> values) {
        for (T value : values) {
            add(value);
        }
    }

    /**
     * Add elements to the bag.
     *
     * @param values    the elements to add
     * @param priority  the priority to assign to the elements
     */
    public void addAll(Iterable<? extends T> values, int priority) {
        for (T value : values) {
            add(value, priority);
        }
    }

    /**
     * Add an element to the bag.
     * <p>
     * If the element's class is annotated with the {@link javax.annotation.Priority}
     * annotation then that value will be used to determine priority otherwise the
     * default priority value will be used.
     *
     * @param value  the element to add
     */
    public void add(T value) {
        if (value != null) {
            Priority annotation = value.getClass().getAnnotation(Priority.class);
            int priority = annotation == null ? defaultPriority : annotation.value();
            add(value, priority);
        }
    }

    /**
     * Add an element to the bag.
     *
     * @param value    the element to add
     * @param priority the priority of the element
     */
    public void add(T value, int priority) {
        if (value != null) {
            if (priority < 0) {
                noPriorityList.add(value);
            } else {
                contents.compute(priority, (key, list) -> combine(list, value));
            }
        }
    }

    /**
     * Obtain the contents of this {@link PriorityBag} as
     * an ordered {@link Stream}.
     *
     * @return the contents of this {@link PriorityBag} as
     *         an ordered {@link Stream}
     */
    public Stream<T> stream() {
        Stream<T> stream = contents.entrySet()
                .stream()
                .flatMap(e -> e.getValue().stream());

        return Stream.concat(stream, noPriorityList.stream());
    }

    @Override
    public Iterator<T> iterator() {
        return stream().iterator();
    }

    private List<T> combine(List<T> list, T value) {
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(value);
        return list;
    }
}
