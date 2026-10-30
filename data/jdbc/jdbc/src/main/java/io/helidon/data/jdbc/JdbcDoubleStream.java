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
import java.util.DoubleSummaryStatistics;
import java.util.NoSuchElementException;
import java.util.Objects;
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
 * Preserves query scope and processing failures across stream pipelines.
 */
final class JdbcDoubleStream implements DoubleStream {

    private final DoubleStream delegate;
    private final JdbcStreamScope scope;
    private boolean linkedOrConsumed;

    JdbcDoubleStream(DoubleStream delegate, JdbcStreamScope scope) {
        this.delegate = delegate;
        this.scope = scope;
    }

    @Override
    public DoubleStream filter(DoublePredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.filter(arg0), scope));
    }

    @Override
    public DoubleStream map(DoubleUnaryOperator arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.map(arg0), scope));
    }

    @Override
    public <U> Stream<U> mapToObj(DoubleFunction<? extends U> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.mapToObj(arg0), scope));
    }

    @Override
    public IntStream mapToInt(DoubleToIntFunction arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.mapToInt(arg0), scope));
    }

    @Override
    public LongStream mapToLong(DoubleToLongFunction arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.mapToLong(arg0), scope));
    }

    @Override
    public DoubleStream flatMap(DoubleFunction<? extends DoubleStream> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.flatMap(arg0), scope));
    }

    @Override
    public DoubleStream mapMulti(DoubleStream.DoubleMapMultiConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.mapMulti(arg0), scope));
    }

    @Override
    public DoubleStream distinct() {
        scope.check();
        return link(new JdbcDoubleStream(delegate.distinct(), scope));
    }

    @Override
    public DoubleStream sorted() {
        scope.check();
        return link(new JdbcDoubleStream(delegate.sorted(), scope));
    }

    @Override
    public DoubleStream peek(DoubleConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.peek(arg0), scope));
    }

    @Override
    public DoubleStream limit(long arg0) {
        scope.check();
        return link(new JdbcDoubleStream(delegate.limit(arg0), scope));
    }

    @Override
    public DoubleStream skip(long arg0) {
        scope.check();
        return link(new JdbcDoubleStream(delegate.skip(arg0), scope));
    }

    @Override
    public DoubleStream takeWhile(DoublePredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.takeWhile(arg0), scope));
    }

    @Override
    public DoubleStream dropWhile(DoublePredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.dropWhile(arg0), scope));
    }

    @Override
    public void forEach(DoubleConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            delegate.forEach(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public void forEachOrdered(DoubleConsumer arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            delegate.forEachOrdered(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public double[] toArray() {
        claim();
        try {
            return delegate.toArray();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public double reduce(double arg0, DoubleBinaryOperator arg1) {
        Objects.requireNonNull(arg1, "The stream argument must not be null.");
        claim();
        try {
            return delegate.reduce(arg0, arg1);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalDouble reduce(DoubleBinaryOperator arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.reduce(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public <R> R collect(Supplier<R> arg0, ObjDoubleConsumer<R> arg1, BiConsumer<R, R> arg2) {
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
    public double sum() {
        claim();
        try {
            return delegate.sum();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalDouble min() {
        claim();
        try {
            return delegate.min();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalDouble max() {
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
    public DoubleSummaryStatistics summaryStatistics() {
        claim();
        try {
            return delegate.summaryStatistics();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public boolean anyMatch(DoublePredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.anyMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public boolean allMatch(DoublePredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.allMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public boolean noneMatch(DoublePredicate arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.noneMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalDouble findFirst() {
        claim();
        try {
            return delegate.findFirst();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public OptionalDouble findAny() {
        claim();
        try {
            return delegate.findAny();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public Stream<Double> boxed() {
        scope.check();
        return link(new JdbcStream<>(delegate.boxed(), scope));
    }

    @Override
    public DoubleStream sequential() {
        scope.check();
        delegate.sequential();
        return this;
    }

    @Override
    public DoubleStream parallel() {
        scope.check();
        delegate.parallel();
        return this;
    }

    @Override
    public PrimitiveIterator.OfDouble iterator() {
        claim();
        var iterator = delegate.iterator();
        return new PrimitiveIterator.OfDouble() {
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
            public double nextDouble() {
                if (!hasNext()) {
                    throw new NoSuchElementException("There are no more query stream values.");
                }
                try {
                    return iterator.nextDouble();
                } catch (RuntimeException | Error failure) {
                    throw scope.fail(failure);
                }
            }

            @Override
            public void forEachRemaining(DoubleConsumer action) {
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
    public Spliterator.OfDouble spliterator() {
        claim();
        var spliterator = delegate.spliterator();
        return new Spliterator.OfDouble() {
            @Override
            public boolean tryAdvance(DoubleConsumer action) {
                Objects.requireNonNull(action, "The row consumer must not be null.");
                scope.check();
                try {
                    return spliterator.tryAdvance(action);
                } catch (RuntimeException | Error failure) {
                    throw scope.fail(failure);
                }
            }

            @Override
            public Spliterator.OfDouble trySplit() {
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
            public Comparator<? super Double> getComparator() {
                scope.check();
                // The JDK represents natural ordering with a null comparator.
                return spliterator.getComparator();
            }
        };
    }



    @Override
    public DoubleStream unordered() {
        scope.check();
        var next = delegate.unordered();
        return next == delegate ? this : link(new JdbcDoubleStream(next, scope));
    }

    @Override
    public DoubleStream onClose(Runnable arg0) {
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
