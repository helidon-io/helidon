/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.testing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Test utility code.
 */
public class Util {

    private Util() {
    }

    /**
     * Converts an iterable of double to a double array.
     *
     * @param iter iterable
     * @return double array
     */
    static double[] doubleArray(Iterable<Double> iter) {
        List<Double> values = new ArrayList<>();
        iter.forEach(values::add);
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    /**
     * Converts an interable of Double to a double array.
     *
     * @param iter iterable of Double
     * @return double array
     */
    static double[] doubleArray(Optional<Iterable<Double>> iter) {
        return iter.map(Util::doubleArray).orElse(null);
    }

    /**
     * Creates a new {@link java.util.List} from an {@link java.lang.Iterable}.
     *
     * @param iterable iterable to convert
     * @param <T>      type of the items
     * @return new list containing the elements reported by the iterable
     */
    static <T> List<T> list(Iterable<T> iterable) {
        List<T> result = new ArrayList<>();
        iterable.forEach(result::add);
        return result;
    }

    static Iterable<Double> iterable(double[] items) {
        return items == null
                ? null
                : () -> new Iterator<>() {

                    private int slot;

                    @Override
                    public boolean hasNext() {
                        return slot < items.length;
                    }

                    @Override
                    public Double next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        return items[slot++];
                    }
                };
    }

    //    static Iterable<io.helidon.metrics.api.Tag> neutralTags(Iterable<Tag> tags) {
    //        return () -> new Iterator<>() {
    //
    //            private final Iterator<Tag> tagsIter = tags.iterator();
    //
    //            @Override
    //            public boolean hasNext() {
    //                return tagsIter.hasNext();
    //            }
    //
    //            @Override
    //            public io.helidon.metrics.api.Tag next() {
    //                Tag next = tagsIter.next();
    //                return io.helidon.metrics.api.Tag.create(next.getKey(), next.getValue());
    //            }
    //        };
    //    }

    //    static <T extends io.helidon.metrics.api.Tag> Iterable<Tag> tags(Iterable<T> tags) {
    //        return () -> new Iterator<>() {
    //
    //            private final Iterator<T> tagsIter = tags.iterator();
    //
    //            @Override
    //            public boolean hasNext() {
    //                return tagsIter.hasNext();
    //            }
    //
    //            @Override
    //            public Tag next() {
    //                io.helidon.metrics.api.Tag next = tagsIter.next();
    //                return Tag.of(next.key(), next.value());
    //            }
    //        };
    //    }
}
