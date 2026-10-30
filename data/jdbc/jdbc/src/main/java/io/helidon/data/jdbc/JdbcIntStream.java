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
import java.util.IntSummaryStatistics;
import java.util.NoSuchElementException;
import java.util.Objects;
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
 * Preserves query scope and processing failures across stream pipelines.
 */
final class JdbcIntStream implements IntStream {

    private final IntStream delegate;
    private final JdbcStreamScope scope;
    private boolean linkedOrConsumed;

    JdbcIntStream(IntStream delegate, JdbcStreamScope scope) {
        this.delegate = delegate;
        this.scope = scope;
    }

    @Override
    public IntStream filter(IntPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.filter(arg0), scope));
    }

    @Override
    public IntStream map(IntUnaryOperator arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.map(arg0), scope));
    }

    @Override
    public <U> Stream<U> mapToObj(IntFunction<? extends U> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.mapToObj(arg0), scope));
    }

    @Override
    public LongStream mapToLong(IntToLongFunction arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.mapToLong(arg0), scope));
    }

    @Override
    public DoubleStream mapToDouble(IntToDoubleFunction arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.mapToDouble(arg0), scope));
    }

    @Override
    public IntStream flatMap(IntFunction<? extends IntStream> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.flatMap(arg0), scope));
    }

    @Override
    public IntStream mapMulti(IntStream.IntMapMultiConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.mapMulti(arg0), scope));
    }

    @Override
    public IntStream distinct() {
        scope.check();
        return link(new JdbcIntStream(delegate.distinct(), scope));
    }

    @Override
    public IntStream sorted() {
        scope.check();
        return link(new JdbcIntStream(delegate.sorted(), scope));
    }

    @Override
    public IntStream peek(IntConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.peek(arg0), scope));
    }

    @Override
    public IntStream limit(long arg0) {
        scope.check();
        return link(new JdbcIntStream(delegate.limit(arg0), scope));
    }

    @Override
    public IntStream skip(long arg0) {
        scope.check();
        return link(new JdbcIntStream(delegate.skip(arg0), scope));
    }

    @Override
    public IntStream takeWhile(IntPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.takeWhile(arg0), scope));
    }

    @Override
    public IntStream dropWhile(IntPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.dropWhile(arg0), scope));
    }

    @Override
    public void forEach(IntConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            delegate.forEach(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public void forEachOrdered(IntConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            delegate.forEachOrdered(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public int[] toArray() {
        claim();
        try {
            return delegate.toArray();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public int reduce(int arg0, IntBinaryOperator arg1) {
        Objects.requireNonNull(arg1, "The stream argument must not be null.");
        claim();
        try {
            return delegate.reduce(arg0, arg1);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalInt reduce(IntBinaryOperator arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.reduce(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public <R> R collect(Supplier<R> arg0, ObjIntConsumer<R> arg1, BiConsumer<R, R> arg2) {
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
    public int sum() {
        claim();
        try {
            return delegate.sum();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalInt min() {
        claim();
        try {
            return delegate.min();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalInt max() {
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
    public IntSummaryStatistics summaryStatistics() {
        claim();
        try {
            return delegate.summaryStatistics();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public boolean anyMatch(IntPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.anyMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public boolean allMatch(IntPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.allMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public boolean noneMatch(IntPredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.noneMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalInt findFirst() {
        claim();
        try {
            return delegate.findFirst();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalInt findAny() {
        claim();
        try {
            return delegate.findAny();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public LongStream asLongStream() {
        scope.check();
        return link(new JdbcLongStream(delegate.asLongStream(), scope));
    }

    @Override
    public DoubleStream asDoubleStream() {
        scope.check();
        return link(new JdbcDoubleStream(delegate.asDoubleStream(), scope));
    }

    @Override
    public Stream<Integer> boxed() {
        scope.check();
        return link(new JdbcStream<>(delegate.boxed(), scope));
    }

    @Override
    public IntStream sequential() {
        scope.check();
        delegate.sequential();
        return this;
    }

    @Override
    public IntStream parallel() {
        scope.check();
        delegate.parallel();
        return this;
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        claim();
        var iterator = delegate.iterator();
        return new PrimitiveIterator.OfInt() {
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
            public int nextInt() {
                if (!hasNext()) {
                    throw new NoSuchElementException("There are no more query stream values.");
                }
                try {
                    return iterator.nextInt();
                } catch (RuntimeException | Error failure) {
                    throw scope.fail(failure);
                }
            }

            @Override
            public void forEachRemaining(IntConsumer action) {
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
    public Spliterator.OfInt spliterator() {
        claim();
        var spliterator = delegate.spliterator();
        return new Spliterator.OfInt() {
            @Override
            public boolean tryAdvance(IntConsumer action) {
                Objects.requireNonNull(action, "The row consumer must not be null.");
                scope.check();
                try {
                    return spliterator.tryAdvance(action);
                } catch (RuntimeException | Error failure) {
                    throw scope.fail(failure);
                }
            }

            @Override
            public Spliterator.OfInt trySplit() {
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
            public Comparator<? super Integer> getComparator() {
                scope.check();
                // The JDK represents natural ordering with a null comparator.
                return spliterator.getComparator();
            }
        };
    }



    @Override
    public IntStream unordered() {
        scope.check();
        var next = delegate.unordered();
        return next == delegate ? this : link(new JdbcIntStream(next, scope));
    }

    @Override
    public IntStream onClose(Runnable arg0) {
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
