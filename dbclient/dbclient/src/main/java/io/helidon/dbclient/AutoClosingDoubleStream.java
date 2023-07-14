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

import java.util.DoubleSummaryStatistics;
import java.util.OptionalDouble;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * An {@link DoubleStream} decorator that invokes a {@link Runnable} on terminal operations.
 * This class supports {@link AutoClosingStream}.
 */
class AutoClosingDoubleStream implements DoubleStream {

    private final DoubleStream delegate;
    private final Runnable closeHandler;

    private AutoClosingDoubleStream(DoubleStream delegate, Runnable closeHandler) {
        this.delegate = delegate;
        this.closeHandler = closeHandler;
    }

    /**
     * Decorate an {@link DoubleStream} to invoke a {@link Runnable} on terminal operations.
     *
     * @param stream       stream to decorate
     * @param closeHandler runnable to invoke on terminal operations
     * @return decorated stream
     */
    static DoubleStream decorate(DoubleStream stream, Runnable closeHandler) {
        if (stream instanceof AutoClosingDoubleStream) {
            return stream;
        }
        return new AutoClosingDoubleStream(stream, AutoClosingHandler.decorate(closeHandler));
    }

    @Override
    public void forEach(DoubleConsumer action) {
        try {
            delegate.forEach(action);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public void forEachOrdered(DoubleConsumer action) {
        try {
            delegate.forEachOrdered(action);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public double[] toArray() {
        try {
            return delegate.toArray();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public double reduce(double identity, DoubleBinaryOperator op) {
        try {
            return delegate.reduce(identity, op);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalDouble reduce(DoubleBinaryOperator op) {
        try {
            return delegate.reduce(op);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjDoubleConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        try {
            return delegate.collect(supplier, accumulator, combiner);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public double sum() {
        try {
            return delegate.sum();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalDouble min() {
        try {
            return delegate.min();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalDouble max() {
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
    public DoubleSummaryStatistics summaryStatistics() {
        try {
            return delegate.summaryStatistics();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean anyMatch(DoublePredicate predicate) {
        try {
            return delegate.anyMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean allMatch(DoublePredicate predicate) {
        try {
            return delegate.allMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean noneMatch(DoublePredicate predicate) {
        try {
            return delegate.noneMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalDouble findFirst() {
        try {
            return delegate.findFirst();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalDouble findAny() {
        try {
            return delegate.findAny();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public DoubleStream filter(DoublePredicate predicate) {
        return decorate(delegate.filter(predicate), closeHandler);
    }

    @Override
    public DoubleStream map(DoubleUnaryOperator mapper) {
        return decorate(delegate.map(mapper), closeHandler);
    }

    @Override
    public <U> Stream<U> mapToObj(DoubleFunction<? extends U> mapper) {
        return AutoClosingStream.decorate(delegate.mapToObj(mapper), closeHandler);
    }

    @Override
    public IntStream mapToInt(DoubleToIntFunction mapper) {
        return AutoClosingIntStream.decorate(delegate.mapToInt(mapper), closeHandler);
    }

    @Override
    public LongStream mapToLong(DoubleToLongFunction mapper) {
        return AutoClosingLongStream.decorate(delegate.mapToLong(mapper), closeHandler);
    }

    @Override
    public DoubleStream flatMap(DoubleFunction<? extends DoubleStream> mapper) {
        return decorate(delegate.flatMap(mapper), closeHandler);
    }

    @Override
    public DoubleStream mapMulti(DoubleMapMultiConsumer mapper) {
        return decorate(delegate.mapMulti(mapper), closeHandler);
    }

    @Override
    public DoubleStream distinct() {
        return decorate(delegate.distinct(), closeHandler);
    }

    @Override
    public DoubleStream sorted() {
        return decorate(delegate.sorted(), closeHandler);
    }

    @Override
    public DoubleStream peek(DoubleConsumer action) {
        return decorate(delegate.peek(action), closeHandler);
    }

    @Override
    public DoubleStream limit(long maxSize) {
        return decorate(delegate.limit(maxSize), closeHandler);
    }

    @Override
    public DoubleStream skip(long n) {
        return decorate(delegate.skip(n), closeHandler);
    }

    @Override
    public DoubleStream takeWhile(DoublePredicate predicate) {
        return decorate(delegate.takeWhile(predicate), closeHandler);
    }

    @Override
    public DoubleStream dropWhile(DoublePredicate predicate) {
        return decorate(delegate.dropWhile(predicate), closeHandler);
    }

    @Override
    public Stream<Double> boxed() {
        return AutoClosingStream.decorate(delegate.boxed(), closeHandler);
    }

    @Override
    public DoubleStream sequential() {
        return decorate(delegate.sequential(), closeHandler);
    }

    @Override
    public DoubleStream parallel() {
        return decorate(delegate.parallel(), closeHandler);
    }

    @Override
    public PrimitiveIterator.OfDouble iterator() {
        PrimitiveIterator.OfDouble iterator = delegate.iterator();
        return new PrimitiveIterator.OfDouble() {
            @Override
            public boolean hasNext() {
                if (iterator.hasNext()) {
                    return true;
                }
                closeHandler.run();
                return false;
            }

            @Override
            public double nextDouble() {
                return iterator.nextDouble();
            }
        };
    }

    @Override
    public Spliterator.OfDouble spliterator() {
        Spliterator.OfDouble spliterator = delegate.spliterator();
        return new Spliterator.OfDouble() {
            @Override
            public boolean tryAdvance(DoubleConsumer action) {
                if (spliterator.tryAdvance(action)) {
                    return true;
                }
                closeHandler.run();
                return false;
            }

            @Override
            public OfDouble trySplit() {
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
    public DoubleStream unordered() {
        return decorate(delegate.unordered(), closeHandler);
    }

    @Override
    public DoubleStream onClose(Runnable closeHandler) {
        return decorate(delegate.onClose(closeHandler), this.closeHandler);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
