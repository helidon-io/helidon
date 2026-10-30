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
package io.helidon.data.jdbc;

import java.util.Comparator;
import java.util.LongSummaryStatistics;
import java.util.NoSuchElementException;
import java.util.Objects;
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
 * Preserves query scope and processing failures across stream pipelines.
 */
final class JdbcLongStream implements LongStream {

    private final LongStream delegate;
    private final JdbcStreamScope scope;
    private boolean linkedOrConsumed;

    JdbcLongStream(LongStream delegate, JdbcStreamScope scope) {
        this.delegate = delegate;
        this.scope = scope;
    }

    @Override
    public LongStream filter(LongPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.filter(arg0), scope));
    }

    @Override
    public LongStream map(LongUnaryOperator arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.map(arg0), scope));
    }

    @Override
    public <U> Stream<U> mapToObj(LongFunction<? extends U> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.mapToObj(arg0), scope));
    }

    @Override
    public IntStream mapToInt(LongToIntFunction arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.mapToInt(arg0), scope));
    }

    @Override
    public DoubleStream mapToDouble(LongToDoubleFunction arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.mapToDouble(arg0), scope));
    }

    @Override
    public LongStream flatMap(LongFunction<? extends LongStream> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.flatMap(arg0), scope));
    }

    @Override
    public LongStream mapMulti(LongStream.LongMapMultiConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.mapMulti(arg0), scope));
    }

    @Override
    public LongStream distinct() {
        scope.check();
        return link(new JdbcLongStream(delegate.distinct(), scope));
    }

    @Override
    public LongStream sorted() {
        scope.check();
        return link(new JdbcLongStream(delegate.sorted(), scope));
    }

    @Override
    public LongStream peek(LongConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.peek(arg0), scope));
    }

    @Override
    public LongStream limit(long arg0) {
        scope.check();
        return link(new JdbcLongStream(delegate.limit(arg0), scope));
    }

    @Override
    public LongStream skip(long arg0) {
        scope.check();
        return link(new JdbcLongStream(delegate.skip(arg0), scope));
    }

    @Override
    public LongStream takeWhile(LongPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.takeWhile(arg0), scope));
    }

    @Override
    public LongStream dropWhile(LongPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.dropWhile(arg0), scope));
    }

    @Override
    public void forEach(LongConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            delegate.forEach(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public void forEachOrdered(LongConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            delegate.forEachOrdered(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public long[] toArray() {
        claim();
        try {
            return delegate.toArray();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public long reduce(long arg0, LongBinaryOperator arg1) {
        Objects.requireNonNull(arg1, "The stream argument must not be null.");
        claim();
        try {
            return delegate.reduce(arg0, arg1);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalLong reduce(LongBinaryOperator arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.reduce(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public <R> R collect(Supplier<R> arg0, ObjLongConsumer<R> arg1, BiConsumer<R, R> arg2) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        Objects.requireNonNull(arg1, "The stream argument must not be null.");
        Objects.requireNonNull(arg2, "The stream argument must not be null.");
        claim();
        try {
            return delegate.collect(arg0, arg1, arg2);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public long sum() {
        claim();
        try {
            return delegate.sum();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalLong min() {
        claim();
        try {
            return delegate.min();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalLong max() {
        claim();
        try {
            return delegate.max();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public long count() {
        claim();
        try {
            return delegate.count();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalDouble average() {
        claim();
        try {
            return delegate.average();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public LongSummaryStatistics summaryStatistics() {
        claim();
        try {
            return delegate.summaryStatistics();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public boolean anyMatch(LongPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.anyMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public boolean allMatch(LongPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.allMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public boolean noneMatch(LongPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.noneMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalLong findFirst() {
        claim();
        try {
            return delegate.findFirst();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalLong findAny() {
        claim();
        try {
            return delegate.findAny();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public DoubleStream asDoubleStream() {
        scope.check();
        return link(new JdbcDoubleStream(delegate.asDoubleStream(), scope));
    }

    @Override
    public Stream<Long> boxed() {
        scope.check();
        return link(new JdbcStream<>(delegate.boxed(), scope));
    }

    @Override
    public LongStream sequential() {
        scope.check();
        delegate.sequential();
        return this;
    }

    @Override
    public LongStream parallel() {
        scope.check();
        delegate.parallel();
        return this;
    }

    @Override
    public PrimitiveIterator.OfLong iterator() {
        claim();
        var iterator = delegate.iterator();
        return new PrimitiveIterator.OfLong() {
            @Override
            public boolean hasNext() {
                scope.check();
                try {
                    return iterator.hasNext();
                } catch (RuntimeException | Error failure) {
                    throw scope.fail(failure);
                }
            }

            @Override
            public long nextLong() {
                if (!hasNext()) {
                    throw new NoSuchElementException("There are no more query stream values.");
                }
                try {
                    return iterator.nextLong();
                } catch (RuntimeException | Error failure) {
                    throw scope.fail(failure);
                }
            }

            @Override
            public void forEachRemaining(LongConsumer action) {
                Objects.requireNonNull(action, "The row consumer must not be null.");
                scope.check();
                try {
                    iterator.forEachRemaining(action);
                } catch (RuntimeException | Error failure) {
                    throw scope.fail(failure);
                }
            }
        };
    }

    @Override
    public Spliterator.OfLong spliterator() {
        claim();
        var spliterator = delegate.spliterator();
        return new Spliterator.OfLong() {
            @Override
            public boolean tryAdvance(LongConsumer action) {
                Objects.requireNonNull(action, "The row consumer must not be null.");
                scope.check();
                try {
                    return spliterator.tryAdvance(action);
                } catch (RuntimeException | Error failure) {
                    throw scope.fail(failure);
                }
            }

            @Override
            public Spliterator.OfLong trySplit() {
                scope.check();
                return null;
            }

            @Override
            public long estimateSize() {
                return spliterator.estimateSize();
            }

            @Override
            public int characteristics() {
                return spliterator.characteristics();
            }

            @Override
            public Comparator<? super Long> getComparator() {
                scope.check();
                // The JDK represents natural ordering with a null comparator.
                return spliterator.getComparator();
            }
        };
    }



    @Override
    public LongStream unordered() {
        scope.check();
        var next = delegate.unordered();
        return next == delegate ? this : link(new JdbcLongStream(next, scope));
    }

    @Override
    public LongStream onClose(Runnable arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        // The JDK rejects registration after consumption before the shared handler list changes.
        delegate.onClose(() -> { });
        scope.onClose(arg0);
        return this;
    }

    @Override
    public boolean isParallel() {
        scope.check();
        return delegate.isParallel();
    }

    @Override
    public void close() {
        scope.checkThread();
        try {
            delegate.close();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }
    private <S> S link(S stage) {
        linkedOrConsumed = true;
        return stage;
    }

    private void claim() {
        scope.check();
        if (linkedOrConsumed) {
            throw new IllegalStateException("The query stream stage has already been linked or consumed.");
        }
        linkedOrConsumed = true;
    }

}
