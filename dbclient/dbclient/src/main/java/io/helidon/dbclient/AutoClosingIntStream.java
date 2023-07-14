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

import java.util.IntSummaryStatistics;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * An {@link IntStream} decorator that invokes a {@link Runnable} on terminal operations.
 * This class supports {@link AutoClosingStream}.
 */
class AutoClosingIntStream implements IntStream {

    private final IntStream delegate;
    private final Runnable closeHandler;

    private AutoClosingIntStream(IntStream delegate, Runnable closeHandler) {
        this.delegate = delegate;
        this.closeHandler = closeHandler;
    }

    /**
     * Decorate an {@link IntStream} to invoke a {@link Runnable} on terminal operations.
     *
     * @param stream       stream to decorate
     * @param closeHandler runnable to invoke on terminal operations
     * @return decorated stream
     */
    static IntStream decorate(IntStream stream, Runnable closeHandler) {
        if (stream instanceof AutoClosingIntStream) {
            return stream;
        }
        return new AutoClosingIntStream(stream, AutoClosingHandler.decorate(closeHandler));
    }

    @Override
    public void forEach(IntConsumer action) {
        try {
            delegate.forEach(action);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public void forEachOrdered(IntConsumer action) {
        try {
            delegate.forEachOrdered(action);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public int[] toArray() {
        try {
            return delegate.toArray();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public int reduce(int identity, IntBinaryOperator op) {
        try {
            return delegate.reduce(identity, op);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalInt reduce(IntBinaryOperator op) {
        try {
            return delegate.reduce(op);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjIntConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        try {
            return delegate.collect(supplier, accumulator, combiner);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public int sum() {
        try {
            return delegate.sum();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalInt min() {
        try {
            return delegate.min();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalInt max() {
        try {
            return delegate.max();
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
    public OptionalDouble average() {
        try {
            return delegate.average();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public IntSummaryStatistics summaryStatistics() {
        try {
            return delegate.summaryStatistics();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean anyMatch(IntPredicate predicate) {
        try {
            return delegate.anyMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean allMatch(IntPredicate predicate) {
        try {
            return delegate.allMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean noneMatch(IntPredicate predicate) {
        try {
            return delegate.noneMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalInt findFirst() {
        try {
            return delegate.findFirst();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalInt findAny() {
        try {
            return delegate.findAny();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public IntStream filter(IntPredicate predicate) {
        try {
            return decorate(delegate.filter(predicate), closeHandler);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public IntStream map(IntUnaryOperator mapper) {
        try {
            return decorate(delegate.map(mapper), closeHandler);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public <U> Stream<U> mapToObj(IntFunction<? extends U> mapper) {
        return AutoClosingStream.decorate(delegate.mapToObj(mapper), closeHandler);
    }

    @Override
    public LongStream mapToLong(IntToLongFunction mapper) {
        return AutoClosingLongStream.decorate(delegate.mapToLong(mapper), closeHandler);
    }

    @Override
    public DoubleStream mapToDouble(IntToDoubleFunction mapper) {
        return AutoClosingDoubleStream.decorate(delegate.mapToDouble(mapper), closeHandler);
    }

    @Override
    public IntStream flatMap(IntFunction<? extends IntStream> mapper) {
        return decorate(delegate.flatMap(mapper), closeHandler);
    }

    @Override
    public IntStream mapMulti(IntMapMultiConsumer mapper) {
        return decorate(delegate.mapMulti(mapper), closeHandler);
    }

    @Override
    public IntStream distinct() {
        return decorate(delegate.distinct(), closeHandler);
    }

    @Override
    public IntStream sorted() {
        return decorate(delegate.sorted(), closeHandler);
    }

    @Override
    public IntStream peek(IntConsumer action) {
        return decorate(delegate.peek(action), closeHandler);
    }

    @Override
    public IntStream limit(long maxSize) {
        return decorate(delegate.limit(maxSize), closeHandler);
    }

    @Override
    public IntStream skip(long n) {
        return decorate(delegate.skip(n), closeHandler);
    }

    @Override
    public IntStream takeWhile(IntPredicate predicate) {
        return decorate(delegate.takeWhile(predicate), closeHandler);
    }

    @Override
    public IntStream dropWhile(IntPredicate predicate) {
        return decorate(delegate.dropWhile(predicate), closeHandler);
    }

    @Override
    public LongStream asLongStream() {
        return AutoClosingLongStream.decorate(delegate.asLongStream(), closeHandler);
    }

    @Override
    public DoubleStream asDoubleStream() {
        return AutoClosingDoubleStream.decorate(delegate.asDoubleStream(), closeHandler);
    }

    @Override
    public Stream<Integer> boxed() {
        return AutoClosingStream.decorate(delegate.boxed(), closeHandler);
    }

    @Override
    public IntStream sequential() {
        return decorate(delegate.sequential(), closeHandler);
    }

    @Override
    public IntStream parallel() {
        return decorate(delegate.parallel(), closeHandler);
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        PrimitiveIterator.OfInt iterator = delegate.iterator();
        return new PrimitiveIterator.OfInt() {
            @Override
            public boolean hasNext() {
                if (iterator.hasNext()) {
                    return true;
                }
                closeHandler.run();
                return false;
            }

            @Override
            public int nextInt() {
                return iterator.nextInt();
            }
        };
    }

    @Override
    public Spliterator.OfInt spliterator() {
        Spliterator.OfInt spliterator = delegate.spliterator();
        return new Spliterator.OfInt() {
            @Override
            public boolean tryAdvance(IntConsumer action) {
                if (spliterator.tryAdvance(action)) {
                    return true;
                }
                closeHandler.run();
                return false;
            }

            @Override
            public OfInt trySplit() {
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
    public IntStream unordered() {
        return decorate(delegate.unordered(), closeHandler);
    }

    @Override
    public IntStream onClose(Runnable closeHandler) {
        return decorate(delegate.onClose(closeHandler), this.closeHandler);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
