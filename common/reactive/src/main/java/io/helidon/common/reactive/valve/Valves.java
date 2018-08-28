/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

import io.helidon.common.reactive.Flow;

/**
 * An utility class for {@link Valve} interface.
 */
public class Valves {

    @SuppressWarnings("rawtypes")
    private static final Valve EMPTY = new EmptyValve<>();

    private Valves() {}

    /**
     * Creates a {@link Valve} instance from provided array.
     * <p>
     * If {@code t array} parameter is {@code null} then returns an empty {@link Valve}.
     *
     * @param t   an array to provide as a {@link Valve}
     * @param <T> a type of the array items
     * @return the new instance
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Valve<T> from(T... t) {
        if (t == null || t.length == 0) {
            return empty();
        }
        return from(Arrays.asList(t));
    }

    /**
     * Creates a {@link Valve} instance from the provided {@link Iterable}.
     * <p>
     * If {@code iterable} parameter is {@code null} then returns an empty {@link Valve}.
     *
     * @param iterable an iterable to provide as a {@link Valve}
     * @param <T>      a type of iterable items
     * @return the new instance
     */
    public static <T> Valve<T> from(Iterable<T> iterable) {
        if (iterable == null) {
            return empty();
        }
        return new IteratorValve<>(iterable.iterator());
    }

    /**
     * Creates a {@link ByteBuffer} based {@link Valve} instance from the provided {@link InputStream}.
     * Each byte buffer will have the provided capacity.
     * <p>
     * Each byte buffer uses a newly allocated memory and as such no pooling is performed.
     *
     * @param stream         the input stream to create the {@link Valve} from
     * @param bufferCapacity the capacity of each buffer of bytes
     * @return the new instance
     */
    public static Valve<ByteBuffer> from(InputStream stream, int bufferCapacity) {
        return from(stream, bufferCapacity, null);
    }

    /**
     * Creates a {@link ByteBuffer} based {@link Valve} instance from the provided {@link InputStream}.
     * Each byte buffer will have the provided capacity.
     * <p>
     * Each byte buffer uses a newly allocated memory and as such no pooling is performed.
     *
     * @param stream          the input stream to create the {@link Valve} from
     * @param bufferCapacity  the capacity of each buffer of bytes
     * @param executorService the executor service to use for an execution of the {@link InputStream#read()}
     *                        (and its overloads) operations that are blocking by its nature.
     * @return the new instance
     */
    public static Valve<ByteBuffer> from(InputStream stream, int bufferCapacity, ExecutorService executorService) {
        if (stream == null) {
            return empty();
        }
        if (executorService != null) {
            return new InputStreamValve.InputStreamExecutorValve(stream, bufferCapacity, executorService);
        } else {
            return new InputStreamValve(stream, bufferCapacity);
        }
    }

    /**
     * Creates a {@link Valve} instance from provided {@link io.helidon.common.reactive.Flow.Publisher}.
     * <p>
     * If {@code publisher} parameter is {@code null} then returns an empty {@link Valve}.
     *
     * @param publisher a publisher to provide as a {@link Valve}
     * @param <T>       a type of published items
     * @return the new instance
     */
    public static <T> Valve<T> from(Flow.Publisher<T> publisher) {
        if (publisher == null) {
            return empty();
        }
        return new PublisherValve<>(publisher);
    }

    /**
     * Returns an empty {@link Valve} - instance, which report complete as soon as handler is registered.
     * <p>
     * For performance reason, this particular Valve accepts any amount of handlers.
     *
     * @param <T> type of the item (which is not there :-) )
     * @return singleton instance
     */
    @SuppressWarnings("unchecked")
    public static <T> Valve<T> empty() {
        return (Valve<T>) EMPTY;
    }

}
