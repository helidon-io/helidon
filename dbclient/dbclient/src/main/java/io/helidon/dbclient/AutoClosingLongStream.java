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

import java.util.LongSummaryStatistics;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * An {@link LongStream} decorator that invokes a {@link Runnable} on terminal operations.
 * This class supports {@link AutoClosingStream}.
 */
class AutoClosingLongStream implements LongStream {

    private final LongStream delegate;
    private final Runnable closeHandler;

    private AutoClosingLongStream(LongStream delegate, Runnable closeHandler) {
        this.delegate = delegate;
        this.closeHandler = closeHandler;
    }

    /**
     * Decorate an {@link LongStream} to invoke a {@link Runnable} on terminal operations.
     *
     * @param stream       stream to decorate
     * @param closeHandler runnable to invoke on terminal operations
     * @return decorated stream
     */
    static LongStream decorate(LongStream stream, Runnable closeHandler) {
        if (stream instanceof AutoClosingLongStream) {
            return stream;
        }
        return new AutoClosingLongStream(stream, AutoClosingHandler.decorate(closeHandler));
    }

    @Override
    public void forEach(LongConsumer action) {
        try {
            delegate.forEach(action);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public void forEachOrdered(LongConsumer action) {
        try {
            delegate.forEachOrdered(action);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public long[] toArray() {
        try {
            return delegate.toArray();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public long reduce(long identity, LongBinaryOperator op) {
        try {
            return delegate.reduce(identity, op);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalLong reduce(LongBinaryOperator op) {
        try {
            return delegate.reduce(op);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjLongConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        try {
            return delegate.collect(supplier, accumulator, combiner);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public long sum() {
        try {
            return delegate.sum();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalLong min() {
        try {
            return delegate.min();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalLong max() {
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
    public LongSummaryStatistics summaryStatistics() {
        try {
            return delegate.summaryStatistics();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean anyMatch(LongPredicate predicate) {
        try {
            return delegate.anyMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean allMatch(LongPredicate predicate) {
        try {
            return delegate.allMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public boolean noneMatch(LongPredicate predicate) {
        try {
            return delegate.noneMatch(predicate);
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalLong findFirst() {
        try {
            return delegate.findFirst();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public OptionalLong findAny() {
        try {
            return delegate.findAny();
        } finally {
            closeHandler.run();
        }
    }

    @Override
    public LongStream filter(LongPredicate predicate) {
        return decorate(delegate.filter(predicate), closeHandler);
    }

    @Override
    public LongStream map(LongUnaryOperator mapper) {
        return decorate(delegate.map(mapper), closeHandler);
    }

    @Override
    public <U> Stream<U> mapToObj(LongFunction<? extends U> mapper) {
        return AutoClosingStream.decorate(delegate.mapToObj(mapper), closeHandler);
    }

    @Override
    public IntStream mapToInt(LongToIntFunction mapper) {
        return AutoClosingIntStream.decorate(delegate.mapToInt(mapper), closeHandler);
    }

    @Override
    public DoubleStream mapToDouble(LongToDoubleFunction mapper) {
        return AutoClosingDoubleStream.decorate(delegate.mapToDouble(mapper), closeHandler);
    }

    @Override
    public LongStream flatMap(LongFunction<? extends LongStream> mapper) {
        return decorate(delegate.flatMap(mapper), closeHandler);
    }

    @Override
    public LongStream mapMulti(LongMapMultiConsumer mapper) {
        return decorate(delegate.mapMulti(mapper), closeHandler);
    }

    @Override
    public LongStream distinct() {
        return decorate(delegate.distinct(), closeHandler);
    }

    @Override
    public LongStream sorted() {
        return decorate(delegate.sorted(), closeHandler);
    }

    @Override
    public LongStream peek(LongConsumer action) {
        return decorate(delegate.peek(action), closeHandler);
    }

    @Override
    public LongStream limit(long maxSize) {
        return decorate(delegate.limit(maxSize), closeHandler);
    }

    @Override
    public LongStream skip(long n) {
        return decorate(delegate.skip(n), closeHandler);
    }

    @Override
    public LongStream takeWhile(LongPredicate predicate) {
        return decorate(delegate.takeWhile(predicate), closeHandler);
    }

    @Override
    public LongStream dropWhile(LongPredicate predicate) {
        return decorate(delegate.dropWhile(predicate), closeHandler);
    }

    @Override
    public DoubleStream asDoubleStream() {
        return AutoClosingDoubleStream.decorate(delegate.asDoubleStream(), closeHandler);
    }

    @Override
    public Stream<Long> boxed() {
        return AutoClosingStream.decorate(delegate.boxed());
    }

    @Override
    public LongStream sequential() {
        return decorate(delegate.sequential(), closeHandler);
    }

    @Override
    public LongStream parallel() {
        return decorate(delegate.parallel(), closeHandler);
    }

    @Override
    public PrimitiveIterator.OfLong iterator() {
        PrimitiveIterator.OfLong iterator = delegate.iterator();
        return new PrimitiveIterator.OfLong() {
            @Override
            public boolean hasNext() {
                if (iterator.hasNext()) {
                    return true;
                }
                closeHandler.run();
                return false;
            }

            @Override
            public long nextLong() {
                return iterator.nextLong();
            }
        };
    }

    @Override
    public Spliterator.OfLong spliterator() {
        Spliterator.OfLong spliterator = delegate.spliterator();
        return new Spliterator.OfLong() {
            @Override
            public boolean tryAdvance(LongConsumer action) {
                if (spliterator.tryAdvance(action)) {
                    return true;
                }
                closeHandler.run();
                return false;
            }

            @Override
            public OfLong trySplit() {
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
    public LongStream unordered() {
        return decorate(delegate.unordered(), closeHandler);
    }

    @Override
    public LongStream onClose(Runnable closeHandler) {
        return decorate(delegate.onClose(closeHandler), this.closeHandler);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
