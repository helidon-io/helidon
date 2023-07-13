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
package io.helidon.dbclient;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * A {@link Stream} decorator that invokes {@link Stream#close()} on terminal operations.
 *
 * @param <T> the type of the stream elements
 */
class AutoClosingStream<T> implements Stream<T> {

    private final Stream<T> delegate;
    private final Runnable closeHandler;

    private AutoClosingStream(Stream<T> delegate, Runnable closeHandler) {
        this.delegate = delegate;
        this.closeHandler = closeHandler;
    }

    /**
     * Decorate a {@link Stream} to invoke {@link Stream#close()} on terminal operations.
     *
     * @param stream stream to decorate
     * @param <T>    the type of the stream elements
     * @return decorated stream
     */
    static <T> Stream<T> decorate(Stream<T> stream) {
        return decorate(stream, stream::close);
    }

    /**
     * Decorate a {@link Stream} to invoke a {@link Runnable} on terminal operations.
     *
     * @param stream       stream to decorate
     * @param closeHandler runnable to invoke on terminal operations
     * @param <T>          the type of the stream elements
     * @return decorated stream
     */
    static <T> Stream<T> decorate(Stream<T> stream, Runnable closeHandler) {
        if (stream instanceof AutoClosingStream<T>) {
            return stream;
        }
        return new AutoClosingStream<>(stream, AutoClosingHandler.decorate(closeHandler));
    }

    @Override
    public Object[] toArray() {
        try {
            return delegate.toArray();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        try {
            return delegate.toArray(generator);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        try {
            return delegate.reduce(identity, accumulator);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        try {
            return delegate.reduce(accumulator);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        try {
            return delegate.reduce(identity, accumulator, combiner);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        try {
            return delegate.collect(supplier, accumulator, combiner);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        try {
            return delegate.collect(collector);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public List<T> toList() {
        try {
            return delegate.toList();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public long count() {
        try {
            return delegate.count();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        try {
            return delegate.anyMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        try {
            return delegate.allMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        try {
            return delegate.noneMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public Optional<T> findFirst() {
        try {
            return delegate.findFirst();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public Optional<T> findAny() {
        try {
            return delegate.findAny();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        try {
            return delegate.min(comparator);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        try {
            return delegate.max(comparator);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        try {
            delegate.forEach(action);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public void forEachOrdered(Consumer<? super T> action) {
        try {
            delegate.forEachOrdered(action);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public Stream<T> filter(Predicate<? super T> predicate) {
        return decorate(delegate.filter(predicate), closeHandler);
    }

    @Override
    public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        return decorate(delegate.map(mapper), closeHandler);
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        return AutoClosingIntStream.decorate(delegate.mapToInt(mapper), closeHandler);
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        return AutoClosingLongStream.decorate(delegate.mapToLong(mapper), closeHandler);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        return AutoClosingDoubleStream.decorate(delegate.mapToDouble(mapper), closeHandler);
    }

    @Override
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return decorate(delegate.flatMap(mapper), closeHandler);
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        return AutoClosingIntStream.decorate(delegate.flatMapToInt(mapper), closeHandler);
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        return AutoClosingLongStream.decorate(delegate.flatMapToLong(mapper), closeHandler);
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        return AutoClosingDoubleStream.decorate(delegate.flatMapToDouble(mapper), closeHandler);
    }

    @Override
    public <R> Stream<R> mapMulti(BiConsumer<? super T, ? super Consumer<R>> mapper) {
        return decorate(delegate.mapMulti(mapper), closeHandler);
    }

    @Override
    public IntStream mapMultiToInt(BiConsumer<? super T, ? super IntConsumer> mapper) {
        return AutoClosingIntStream.decorate(delegate.mapMultiToInt(mapper), closeHandler);
    }

    @Override
    public LongStream mapMultiToLong(BiConsumer<? super T, ? super LongConsumer> mapper) {
        return AutoClosingLongStream.decorate(delegate.mapMultiToLong(mapper), closeHandler);
    }

    @Override
    public DoubleStream mapMultiToDouble(BiConsumer<? super T, ? super DoubleConsumer> mapper) {
        return AutoClosingDoubleStream.decorate(delegate.mapMultiToDouble(mapper), closeHandler);
    }

    @Override
    public Stream<T> distinct() {
        return decorate(delegate.distinct(), closeHandler);
    }

    @Override
    public Stream<T> sorted() {
        return decorate(delegate.sorted(), closeHandler);
    }

    @Override
    public Stream<T> sorted(Comparator<? super T> comparator) {
        return decorate(delegate.sorted(comparator), closeHandler);
    }

    @Override
    public Stream<T> peek(Consumer<? super T> action) {
        return decorate(delegate.peek(action), closeHandler);
    }

    @Override
    public Stream<T> limit(long maxSize) {
        return decorate(delegate.limit(maxSize), closeHandler);
    }

    @Override
    public Stream<T> skip(long n) {
        return decorate(delegate.skip(n), closeHandler);
    }

    @Override
    public Stream<T> takeWhile(Predicate<? super T> predicate) {
        return decorate(delegate.takeWhile(predicate), closeHandler);
    }

    @Override
    public Stream<T> dropWhile(Predicate<? super T> predicate) {
        return decorate(delegate.dropWhile(predicate), closeHandler);
    }

    @Override
    public Iterator<T> iterator() {
        Iterator<T> iterator = delegate.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                if (iterator.hasNext()) {
                    return true;
                }
                closeHandler.run();
                return false;
            }

            @Override
            public T next() {
                return iterator.next();
            }
        };
    }

    @Override
    public Spliterator<T> spliterator() {
        Spliterator<T> spliterator = delegate.spliterator();
        return new Spliterator<>() {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                if (spliterator.tryAdvance(action)) {
                    return true;
                }
                closeHandler.run();
                return false;
            }

            @Override
            public Spliterator<T> trySplit() {
                return spliterator.trySplit();
            }

            @Override
            public long estimateSize() {
                return spliterator.estimateSize();
            }

            @Override
            public int characteristics() {
                return spliterator.characteristics();
            }
        };
    }

    @Override
    public boolean isParallel() {
        return delegate.isParallel();
    }

    @Override
    public Stream<T> sequential() {
        return decorate(delegate.sequential(), closeHandler);
    }

    @Override
    public Stream<T> parallel() {
        return decorate(delegate.parallel(), closeHandler);
    }

    @Override
    public Stream<T> unordered() {
        return decorate(delegate.unordered(), closeHandler);
    }

    @Override
    public Stream<T> onClose(Runnable closeHandler) {
        return decorate(delegate.onClose(closeHandler), this.closeHandler);
    }

    @Override
    public void close() {
        closeHandler.run();
    }
}
