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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
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
import java.util.stream.Gatherer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Preserves query scope and processing failures across stream pipelines.
 */
final class JdbcStream<T> implements Stream<T> {

    private final Stream<T> delegate;
    private final JdbcStreamScope scope;
    private boolean linkedOrConsumed;

    JdbcStream(Stream<T> delegate, JdbcStreamScope scope) {
        this.delegate = delegate;
        this.scope = scope;
    }

    @Override
    public Stream<T> filter(Predicate<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.filter(arg0), scope));
    }

    @Override
    public <R> Stream<R> map(Function<? super T, ? extends R> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.map(arg0), scope));
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.mapToInt(arg0), scope));
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.mapToLong(arg0), scope));
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.mapToDouble(arg0), scope));
    }

    @Override
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.flatMap(arg0), scope));
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.flatMapToInt(arg0), scope));
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.flatMapToLong(arg0), scope));
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.flatMapToDouble(arg0), scope));
    }

    @Override
    public <R> Stream<R> mapMulti(BiConsumer<? super T, ? super Consumer<R>> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.mapMulti(arg0), scope));
    }

    @Override
    public IntStream mapMultiToInt(BiConsumer<? super T, ? super IntConsumer> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcIntStream(delegate.mapMultiToInt(arg0), scope));
    }

    @Override
    public LongStream mapMultiToLong(BiConsumer<? super T, ? super LongConsumer> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcLongStream(delegate.mapMultiToLong(arg0), scope));
    }

    @Override
    public DoubleStream mapMultiToDouble(BiConsumer<? super T, ? super DoubleConsumer> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcDoubleStream(delegate.mapMultiToDouble(arg0), scope));
    }

    @Override
    public Stream<T> distinct() {
        scope.check();
        return link(new JdbcStream<>(delegate.distinct(), scope));
    }

    @Override
    public Stream<T> sorted() {
        scope.check();
        return link(new JdbcStream<>(delegate.sorted(), scope));
    }

    @Override
    public Stream<T> sorted(Comparator<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.sorted(arg0), scope));
    }

    @Override
    public Stream<T> peek(Consumer<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.peek(arg0), scope));
    }

    @Override
    public Stream<T> limit(long arg0) {
        scope.check();
        return link(new JdbcStream<>(delegate.limit(arg0), scope));
    }

    @Override
    public Stream<T> skip(long arg0) {
        scope.check();
        return link(new JdbcStream<>(delegate.skip(arg0), scope));
    }

    @Override
    public Stream<T> takeWhile(Predicate<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.takeWhile(arg0), scope));
    }

    @Override
    public Stream<T> dropWhile(Predicate<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.dropWhile(arg0), scope));
    }

    @Override
    public void forEach(Consumer<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            delegate.forEach(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public void forEachOrdered(Consumer<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            delegate.forEachOrdered(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public Object[] toArray() {
        claim();
        try {
            return delegate.toArray();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.toArray(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public T reduce(T arg0, BinaryOperator<T> arg1) {
        // The JDK Stream contract permits a null reduction identity.
        Objects.requireNonNull(arg1, "The stream argument must not be null.");
        claim();
        try {
            return delegate.reduce(arg0, arg1);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.reduce(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public <U> U reduce(U arg0, BiFunction<U, ? super T, U> arg1, BinaryOperator<U> arg2) {
        // The JDK Stream contract permits a null reduction identity.
        Objects.requireNonNull(arg1, "The stream argument must not be null.");
        Objects.requireNonNull(arg2, "The stream argument must not be null.");
        claim();
        try {
            return delegate.reduce(arg0, arg1, arg2);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public <R> Stream<R> gather(Gatherer<? super T, ?, R> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        scope.check();
        return link(new JdbcStream<>(delegate.gather(arg0), scope));
    }

    @Override
    public <R> R collect(Supplier<R> arg0, BiConsumer<R, ? super T> arg1, BiConsumer<R, R> arg2) {
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
    public <R, A> R collect(Collector<? super T, A, R> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.collect(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public List<T> toList() {
        claim();
        try {
            return delegate.toList();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public Optional<T> min(Comparator<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.min(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public Optional<T> max(Comparator<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.max(arg0);
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
    public boolean anyMatch(Predicate<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.anyMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public boolean allMatch(Predicate<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.allMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public boolean noneMatch(Predicate<? super T> arg0) {
        Objects.requireNonNull(arg0, "The stream argument must not be null.");
        claim();
        try {
            return delegate.noneMatch(arg0);
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public Optional<T> findFirst() {
        claim();
        try {
            return delegate.findFirst();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public Optional<T> findAny() {
        claim();
        try {
            return delegate.findAny();
        } catch (RuntimeException | Error failure) {
            throw scope.fail(failure);
        }
    }

    @Override
    public Stream<T> sequential() {
        scope.check();
        delegate.sequential();
        return this;
    }

    @Override
    public Stream<T> parallel() {
        scope.check();
        delegate.parallel();
        return this;
    }

    @Override
    public Stream<T> unordered() {
        scope.check();
        var next = delegate.unordered();
        return next == delegate ? this : link(new JdbcStream<>(next, scope));
    }

    @Override
    public Stream<T> onClose(Runnable arg0) {
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

    @Override
    public Iterator<T> iterator() {
        claim();
        var iterator = delegate.iterator();
        return new Iterator<>() {
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
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("There are no more query stream values.");
                }
                try {
                    return iterator.next();
                } catch (RuntimeException | Error failure) {
                    throw scope.fail(failure);
                }
            }

            @Override
            public void forEachRemaining(Consumer<? super T> action) {
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
    public Spliterator<T> spliterator() {
        claim();
        var spliterator = delegate.spliterator();
        return new Spliterator<>() {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                Objects.requireNonNull(action, "The row consumer must not be null.");
                scope.check();
                try {
                    return spliterator.tryAdvance(action);
                } catch (RuntimeException | Error failure) {
                    throw scope.fail(failure);
                }
            }

            @Override
            public Spliterator<T> trySplit() {
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
            public Comparator<? super T> getComparator() {
                scope.check();
                // The JDK represents natural ordering with a null comparator.
                return spliterator.getComparator();
            }
        };
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
