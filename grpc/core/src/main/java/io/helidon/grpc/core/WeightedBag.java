/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;

/**
 * A bag of values ordered by weight. All weights must be greater or equal to 0.
 * <p>
 * The higher the weight the higher the priority. For cases where the weight is the same,
 * elements are returned in the order that they were added to the bag.
 *
 * @param <T> the type of elements in the bag
 * @see io.helidon.common.Weight
 */
public class WeightedBag<T> implements Iterable<T> {

    private final Map<Double, List<T>> contents;
    private final double defaultWeight;

    private WeightedBag(Map<Double, List<T>> contents, double defaultWeight) {
        this.contents = contents;
        if (defaultWeight < 0.0) {
            throw new IllegalArgumentException("Weights must be greater or equal to 0");
        }
        this.defaultWeight = defaultWeight;
    }

    /**
     * Create a new {@link WeightedBag} where elements added with no weight will be
     * given a default weight of 0.
     *
     * @param <T> the type of elements in the bag
     * @return a new {@link WeightedBag} where elements added
     * with no weight will be given {@link Weighted#DEFAULT_WEIGHT}
     */
    public static <T> WeightedBag<T> create() {
        return create(Weighted.DEFAULT_WEIGHT);
    }

    /**
     * Create a new {@link WeightedBag} where elements added with no weight will be
     * given a default weight of 0.
     *
     * @param defaultWeight default weight for elements
     * @param <T> the type of elements in the bag
     * @return a new {@link WeightedBag} where elements added
     * with no weight will be given {@link Weighted#DEFAULT_WEIGHT}
     */
    public static <T> WeightedBag<T> create(double defaultWeight) {
        return new WeightedBag<>(new TreeMap<>(
                (o1, o2) -> Double.compare(o2, o1)),        // reversed for weights
                defaultWeight);
    }

    /**
     * Check if bag is empty.
     *
     * @return outcome of test
     */
    public boolean isEmpty() {
        return contents.isEmpty();
    }

    /**
     * Obtain an immutable copy of this {@link WeightedBag}.
     *
     * @return an immutable copy of this {@link WeightedBag}
     */
    public WeightedBag<T> readOnly() {
        return new WeightedBag<>(Collections.unmodifiableMap(contents), defaultWeight);
    }

    /**
     * Merge a {@link WeightedBag} into this {@link WeightedBag}.
     *
     * @param bag the bag to merge
     */
    public void merge(WeightedBag<? extends T> bag) {
        bag.contents.forEach((weight, value) -> addAll(value, weight));
    }

    /**
     * Obtain a copy of this {@link WeightedBag}.
     *
     * @return a copy of this {@link WeightedBag}
     */
    public WeightedBag<T> copyMe() {
        WeightedBag<T> copy = WeightedBag.create();
        copy.merge(this);
        return copy;
    }

    /**
     * Add elements to the bag.
     * <p>
     * If the element's class is annotated with the {@link io.helidon.common.Weight}
     * annotation then that value will be used to determine weight otherwise the
     * default weight value will be used.
     *
     * @param values the elements to add
     */
    public void addAll(Iterable<? extends T> values) {
        for (T value : values) {
            add(value);
        }
    }

    /**
     * Add elements to the bag with a given weight.
     *
     * @param values the elements to add
     * @param weight the weight to assign to the elements
     */
    public void addAll(Iterable<? extends T> values, double weight) {
        for (T value : values) {
            add(value, weight);
        }
    }

    /**
     * Add an element to the bag.
     * <p>
     * If the element's class is annotated with the {@link io.helidon.common.Weight}
     * annotation then that value will be used to determine weight otherwise the
     * default weight value will be used.
     *
     * @param value the element to add
     */
    public void add(T value) {
        Objects.requireNonNull(value);
        double weight;
        if (value instanceof Weighted weighted) {
            weight = weighted.weight();
        } else {
            Weight annotation = value.getClass().getAnnotation(Weight.class);
            weight = (annotation == null) ? defaultWeight : annotation.value();
        }
        add(value, weight);
    }

    /**
     * Add an element to the bag with a specific weight.
     *
     * @param value the element to add
     * @param weight the weight of the element
     */
    public void add(T value, double weight) {
        Objects.requireNonNull(value);
        if (weight < 0.0) {
            throw new IllegalArgumentException("Weights must be greater or equal to 0");
        }
        contents.compute(weight, (key, list) -> {
            List<T> newList = list;
            if (newList == null) {
                newList = new ArrayList<>();
            }
            newList.add(value);
            return newList;
        });
    }

    /**
     * Obtain the contents of this {@link WeightedBag} as
     * an ordered {@link Stream}.
     *
     * @return the contents of this {@link WeightedBag} as
     * an ordered {@link Stream}
     */
    public Stream<T> stream() {
        return contents.entrySet()
                .stream()
                .flatMap(e -> e.getValue().stream());
    }

    @Override
    public Iterator<T> iterator() {
        return stream().iterator();
    }
}
