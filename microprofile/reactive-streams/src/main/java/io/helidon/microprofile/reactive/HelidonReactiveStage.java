/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.microprofile.reactive;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;


import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.ToGraphable;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Named stage-type implementations for better debugging.
 */
final class HelidonReactiveStage {

    /** Type-holder class. */
    private HelidonReactiveStage() {
        throw new IllegalStateException("No instances!");
    }

    static Graph getGraph(PublisherBuilder<?> builder) {
        if (builder instanceof ToGraphable) {
            return ((ToGraphable) builder).toGraph();
        }
        return HelidonReactiveGraphCaptureEngine.capture(builder);
    }

    static Graph getGraph(ProcessorBuilder<?, ?> builder) {
        if (builder instanceof ToGraphable) {
            return ((ToGraphable) builder).toGraph();
        }
        return HelidonReactiveGraphCaptureEngine.capture(builder);
    }

    static Graph getGraph(SubscriberBuilder<?, ?> builder) {
        if (builder instanceof ToGraphable) {
            return ((ToGraphable) builder).toGraph();
        }
        return HelidonReactiveGraphCaptureEngine.capture(builder);
    }

    /** Holder for a {@link Publisher}. */
    static final class HRSPublisher implements Stage.PublisherStage {

        private final Publisher<?> field;

        HRSPublisher(Publisher<?> field) {
            this.field = field;
        }

        @Override
        public Publisher<?> getRsPublisher() {
            return field;
        }
    }

    /** Holder for an {@link Iterable}. */
    static final class HRSIterable implements Stage.Of {

        private final Iterable<?> field;

        HRSIterable(Iterable<?> field) {
            this.field = field;
        }

        @Override
        public Iterable<?> getElements() {
            return field;
        }
    }

    /** The singleton for an empty (and thus stateless) HRSIterable. */
    static final HRSIterable EMPTY = new HRSIterable(Collections.emptyList());

    /** Holder for a {@link Throwable}. */
    static final class HRSFailed implements Stage.Failed {

        private final Throwable field;

        HRSFailed(Throwable field) {
            this.field = field;
        }

        @Override
        public Throwable getError() {
            return field;
        }
    }

    /** Holder for a {@link Processor}. */
    static final class HRSProcessor implements Stage.ProcessorStage {

        private final Processor<?, ?> field;

        HRSProcessor(Processor<?, ?> field) {
            this.field = field;
        }

        @Override
        public Processor<?, ?> getRsProcessor() {
            return field;
        }
    }

    /** Holder for a {@link Subscriber}. */
    static final class HRSSubscriber implements Stage.SubscriberStage {

        private final Subscriber<?> field;

        HRSSubscriber(Subscriber<?> field) {
            this.field = field;
        }

        @Override
        public Subscriber<?> getRsSubscriber() {
            return field;
        }
    }

    /**
     * Holds an Iterable implementation that iterates via a function, exposed
     * as a Stage.Of because there is no designated Stage type for it.
     * @param <T> the element type of the sequence
     */
    static final class HRSIterate<T> implements Stage.Of, Iterable<T> {

        private final T seed;
        private final UnaryOperator<T> f;

        HRSIterate(T seed, UnaryOperator<T> f) {
            this.seed = seed;
            this.f = f;
        }

        @Override
        public Iterable<?> getElements() {
            return this;
        }

        @Override
        public Iterator<T> iterator() {
            return new HRSIterator<>(seed, f);
        }

        static final class HRSIterator<T> implements Iterator<T> {

            private final UnaryOperator<T> f;

            private T current;

            private boolean once;

            HRSIterator(T current, UnaryOperator<T> f) {
                this.f = f;
                this.current = current;
            }

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public T next() {
                if (!once) {
                    once = true;
                } else {
                    current = f.apply(current);
                }
                return current;
            }
        }
    }

    /**
     * Holds a {@link Supplier} that is exposed as an infinite Iterable/Iterator
     * and Stage.Of because there is no designated Stage type for it.
     * @param <T> the element type generated
     */
    static final class HRSGenerate<T> implements Stage.Of, Iterable<T>, Iterator<T> {

        private final Supplier<? extends T> supplier;

        HRSGenerate(Supplier<? extends T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public Iterator<T> iterator() {
            return this;
        }

        @Override
        public Iterable<?> getElements() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public T next() {
            return supplier.get();
        }
    }

    /** Holds onto two {@link Graph}s of two {@link Publisher}s. */
    static final class HRSConcat implements Stage.Concat {

        private final Graph first;

        private final Graph second;

        HRSConcat(Graph first, Graph second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public Graph getFirst() {
            return first;
        }

        @Override
        public Graph getSecond() {
            return second;
        }
    }

    /**
     * Holds onto a {@link CompletionStage}.
     */
    static final class HRSCompletionStage implements Stage.FromCompletionStage {

        private final CompletionStage<?> field;

        HRSCompletionStage(CompletionStage<?> field) {
            this.field = field;
        }

        @Override
        public CompletionStage<?> getCompletionStage() {
            return field;
        }
    }

    /**
     * Holds onto a nullable {@link CompletionStage}.
     */
    static final class HRSCompletionStageNullable implements Stage.FromCompletionStageNullable {

        private final CompletionStage<?> field;

        HRSCompletionStageNullable(CompletionStage<?> field) {
            this.field = field;
        }

        @Override
        public CompletionStage<?> getCompletionStage() {
            return field;
        }
    }

    /** Holds onto a {@link Subscriber} {@link Graph} and a {@link Publisher} {@link Graph}. */
    static final class HRSCoupled implements Stage.Coupled {

        private final Graph subscriber;

        private final Graph publisher;

        HRSCoupled(Graph subscriber, Graph publisher) {
            this.subscriber = subscriber;
            this.publisher = publisher;
        }

        HRSCoupled(Subscriber<?> subscriber, Publisher<?> publisher) {
            this.subscriber = new GraphCollection(Collections.singletonList(new HRSSubscriber(subscriber)));
            this.publisher = new GraphCollection(Collections.singletonList(new HRSPublisher(publisher)));
        }

        @Override
        public Graph getSubscriber() {
            return subscriber;
        }

        @Override
        public Graph getPublisher() {
            return publisher;
        }
    }

    /** Holds onto a Collection of {@link Stage}s. */
    static final class GraphCollection implements Graph {

        private final Collection<Stage> stage;

        GraphCollection(Collection<Stage> stage) {
            this.stage = stage;
        }

        @Override
        public Collection<Stage> getStages() {
            return stage;
        }
    }

    /** Holds onto a {@link Function}. */
    static final class HRSMap implements Stage.Map {

        private final Function<?, ?> field;

        HRSMap(Function<?, ?> field) {
            this.field = field;
        }

        @Override
        public Function<?, ?> getMapper() {
            return field;
        }
    }

    /**
     * Holds onto a {@link Function} that returns a {@link PublisherBuilder}.
     * @param <T> the source value type
     * @param <S> the output type
     */
    static final class HRSFlatMap<T, S> implements Stage.FlatMap, Function<T, Graph> {

        private final Function<? super T, ? extends PublisherBuilder<? extends S>> field;

        HRSFlatMap(Function<? super T, ? extends  PublisherBuilder<? extends S>> field) {
            this.field = field;
        }

        @Override
        public Function<?, Graph> getMapper() {
            return this;
        }

        @Override
        public Graph apply(T o) {
            return getGraph(field.apply(o));
        }
    }

    /**
     * Holds onto a {@link Function} that returns a {@link Publisher}.
     * @param <T> the source value type
     * @param <S> the output type
     */
    static final class HRSFlatMapRs<T, S> implements Stage.FlatMap, Function<T, Graph> {

        private final Function<? super T, ? extends Publisher<? extends S>> field;

        HRSFlatMapRs(Function<? super T, ? extends Publisher<? extends S>> field) {
            this.field = field;
        }

        @Override
        public Function<?, Graph> getMapper() {
            return this;
        }

        @Override
        public Graph apply(T o) {
            return new GraphCollection(Collections.singletonList(new HRSPublisher(field.apply(o))));
        }
    }

    /**
     * Holds onto a {@link Function} that returns a {@link Publisher}.
     * @param <T> the source value type
     * @param <S> the output type
     */
    static final class HRSFlatMapCompletionStage<T, S> implements Stage.FlatMapCompletionStage {

        private final Function<? super T, ? extends CompletionStage<? extends S>> field;

        HRSFlatMapCompletionStage(Function<? super T, ? extends CompletionStage<? extends S>> field) {
            this.field = field;
        }

        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public Function<?, CompletionStage<?>> getMapper() {
            return (Function) field;
        }
    }

    /**
     * Holds onto a @link Function} that returns an {@link Iterable}.
     * @param <T> the source value type
     * @param <S> the output type
     */
    static final class HRSFlatMapIterable<T, S> implements Stage.FlatMapIterable {

        private final Function<? super T, ? extends Iterable<? extends S>> mapper;

        HRSFlatMapIterable(Function<? super T, ? extends Iterable<? extends S>> mapper) {
            this.mapper = mapper;
        }

        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public Function<?, Iterable<?>> getMapper() {
            return (Function) mapper;
        }
    }

    /**
     * Holds onto a {@link Predicate}.
     */
    static final class HRSFilter implements Stage.Filter {

        private final Predicate<?> field;

        HRSFilter(Predicate<?> field) {
            this.field = field;
        }

        @Override
        public Predicate<?> getPredicate() {
            return field;
        }
    }

    /** Indicates a distinct operation. */
    enum HRSDistinct implements  Stage.Distinct {
        INSTANCE
    }

    /** Holds onto the item count for limit. */
    static final class HRSLimit implements Stage.Limit {

        private final long field;

        HRSLimit(long field) {
            this.field = field;
        }

        @Override
        public long getLimit() {
            return field;
        }
    }

    /** Holds onto the item count for skip. */
    static final class HRSSkip implements Stage.Skip {

        private final long field;

        HRSSkip(long field) {
            this.field = field;
        }

        @Override
        public long getSkip() {
            return field;
        }
    }

    /**
     * Holds onto a {@link Predicate}.
     */
    static final class HRSTakeWhile implements Stage.TakeWhile {

        private final Predicate<?> field;

        HRSTakeWhile(Predicate<?> field) {
            this.field = field;
        }

        @Override
        public Predicate<?> getPredicate() {
            return field;
        }
    }

    /**
     * Holds onto a {@link Predicate}.
     */
    static final class HRSDropWhile implements Stage.DropWhile {

        private final Predicate<?> field;

        HRSDropWhile(Predicate<?> field) {
            this.field = field;
        }

        @Override
        public Predicate<?> getPredicate() {
            return field;
        }
    }

    /**
     * Holds onto a {@link Consumer}.
     */
    static final class HRSPeek implements Stage.Peek {

        private final Consumer<?> field;

        HRSPeek(Consumer<?> field) {
            this.field = field;
        }

        @Override
        public Consumer<?> getConsumer() {
            return field;
        }
    }

    /**
     * Holds onto a {@link Throwable} {@link Consumer}.
     */
    static final class HRSOnError implements Stage.OnError {

        private final Consumer<Throwable> field;

        HRSOnError(Consumer<Throwable> field) {
            this.field = field;
        }

        @Override
        public Consumer<Throwable> getConsumer() {
            return field;
        }
    }

    /**
     * Holds onto a {@link Runnable}.
     */
    static final class HRSOnTerminate implements Stage.OnTerminate {

        private final Runnable field;

        HRSOnTerminate(Runnable field) {
            this.field = field;
        }

        @Override
        public Runnable getAction() {
            return field;
        }
    }

    /**
     * Holds onto a {@link Runnable}.
     */
    static final class HRSOnComplete implements Stage.OnComplete {

        private final Runnable field;

        HRSOnComplete(Runnable field) {
            this.field = field;
        }

        @Override
        public Runnable getAction() {
            return field;
        }
    }

    /** Indicates a cancelling consumer. */
    enum HRSCancel implements Stage.Cancel {
        INSTANCE
    }

    /**
     * Holds onto a {@link java.util.stream.Stream} {@link Collector}.
     */
    static final class HRSCollect implements Stage.Collect {

        private final Collector<?, ?, ?> field;

        HRSCollect(Collector<?, ?, ?> field) {
            this.field = field;
        }

        @Override
        public Collector<?, ?, ?> getCollector() {
            return field;
        }
    }

    /**
     * Holds onto a {@link java.util.stream.Stream} {@link Collector}.
     */
    static final class HRSForEach<T> implements Stage.Collect,
            Collector<Object, Object, Object>,
            BiConsumer<Object, Object>,
            Function<Object, Object> {

        private final Consumer<Object> field;

        @SuppressWarnings("unchecked")
        HRSForEach(Consumer<? super T> field) {
            this.field = (Consumer<Object>) field;
        }

        @Override
        public Collector<?, ?, ?> getCollector() {
            return this;
        }

        @Override
        public Supplier<Object> supplier() {
            return NullSupplierCombiner.INSTANCE;
        }

        @Override
        public BiConsumer<Object, Object> accumulator() {
            return this;
        }

        @Override
        public BinaryOperator<Object> combiner() {
            return NullSupplierCombiner.INSTANCE;
        }

        @Override
        public Function<Object, Object> finisher() {
            return this;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }

        @Override
        public void accept(Object o, Object o2) {
            field.accept(o2);
        }

        @Override
        public Object apply(Object o) {
            return o;
        }
    }

    /** Implements Supplier and BinaryOperator which return null. */
    enum NullSupplierCombiner implements Supplier<Object>, BinaryOperator<Object>  {
        INSTANCE;

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object apply(Object o, Object o2) {
            return null;
        }
    }


    /**
     * Holds onto a {@link java.util.stream.Stream} {@link Collector}.
     */
    enum HRSIgnore implements Stage.Collect,
            Collector<Object, Object, Object>,
            BiConsumer<Object, Object>,
            Function<Object, Object> {
        INSTANCE;

        @Override
        public Collector<?, ?, ?> getCollector() {
            return this;
        }

        @Override
        public Supplier<Object> supplier() {
            return NullSupplierCombiner.INSTANCE;
        }

        @Override
        public BiConsumer<Object, Object> accumulator() {
            return this;
        }

        @Override
        public BinaryOperator<Object> combiner() {
            return NullSupplierCombiner.INSTANCE;
        }

        @Override
        public Function<Object, Object> finisher() {
            return this;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }

        @Override
        public void accept(Object o, Object o2) {
        }

        @Override
        public Object apply(Object o) {
            return o;
        }
    }

    /** Indicates a cancelling consumer. */
    enum HRSFindFirst implements Stage.FindFirst {
        INSTANCE
    }

    /**
     * Encapsulates an reducer with initial value.
     * @param <T> the element type
     */
    static final class HRSReduceFull<T> implements Stage.Collect,
            Collector<Object, Object, Object>,
            BiConsumer<Object, Object>,
            Function<Object, Object>,
            Supplier<Object> {

        private final T identity;

        private final BinaryOperator<T> accumulator;

        HRSReduceFull(T identity, BinaryOperator<T> accumulator) {
            this.identity = identity;
            this.accumulator = accumulator;
        }

        @Override
        public Collector<Object, Object, Object> getCollector() {
            return this;
        }

        @Override
        public Supplier<Object> supplier() {
            return this;
        }

        @Override
        public BiConsumer<Object, Object> accumulator() {
            return this;
        }

        @Override
        public BinaryOperator<Object> combiner() {
            return null;
        }

        @Override
        public Function<Object, Object> finisher() {
            return this;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }

        @Override
        @SuppressWarnings("unchecked")
        public void accept(Object o, Object o2) {
            AtomicReference<T> ref = (AtomicReference<T>) o;
            ref.lazySet(accumulator.apply(ref.get(), (T) o2));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object apply(Object o) {
            return ((AtomicReference<Object>) o).get();
        }

        @Override
        public Object get() {
            return new AtomicReference<>(identity);
        }
    }

    /**
     * Encapsulates an reducer without an initial value and optional output.
     * @param <T> the element type
     */
    static final class HRSReduceOptional<T> implements Stage.Collect,
            Collector<Object, Object, Object>,
            BiConsumer<Object, Object>,
            Function<Object, Object>,
            Supplier<Object> {

        private final BinaryOperator<T> accumulator;

        HRSReduceOptional(BinaryOperator<T> accumulator) {
            this.accumulator = accumulator;
        }

        @Override
        public Collector<Object, Object, Object> getCollector() {
            return this;
        }

        @Override
        public Supplier<Object> supplier() {
            return this;
        }

        @Override
        public BiConsumer<Object, Object> accumulator() {
            return this;
        }

        @Override
        public BinaryOperator<Object> combiner() {
            return null;
        }

        @Override
        public Function<Object, Object> finisher() {
            return this;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }

        @Override
        @SuppressWarnings("unchecked")
        public void accept(Object o, Object o2) {
            AtomicReference<T> ref = (AtomicReference<T>) o;
            if (ref.get() == null) {
                ref.lazySet((T) o2);
            } else {
                ref.lazySet(accumulator.apply(ref.get(), (T) o2));
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object apply(Object o) {
            return Optional.ofNullable(((AtomicReference<Object>) o).get());
        }

        @Override
        public Object get() {
            return new AtomicReference<T>();
        }
    }

    /**
     * Encapsulates an reducer with initial value.
     * @param <T> the element type
     */
    static final class HRSCollectFull<T, R> implements Stage.Collect,
            Collector<Object, Object, Object>,
            Function<Object, Object> {

        private final Supplier<R> identity;

        private final BiConsumer<R, ? super T> accumulator;

        HRSCollectFull(Supplier<R> identity, BiConsumer<R, ? super T> accumulator) {
            this.identity = identity;
            this.accumulator = accumulator;
        }

        @Override
        public Collector<Object, Object, Object> getCollector() {
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Supplier<Object> supplier() {
            return (Supplier<Object>) identity;
        }

        @Override
        @SuppressWarnings("unchecked")
        public BiConsumer<Object, Object> accumulator() {
            return (BiConsumer<Object, Object>) accumulator;
        }

        @Override
        public BinaryOperator<Object> combiner() {
            return null;
        }

        @Override
        public Function<Object, Object> finisher() {
            return this;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }

        @Override
        public Object apply(Object o) {
            return o;
        }
    }

    /**
     * Holds onto a {@link Function} taking a {@link Throwable}.
     */
    static final class HRSOnErrorResume implements Stage.OnErrorResume {

        private final Function<Throwable, ?> field;

        HRSOnErrorResume(Function<Throwable, ?> field) {
            this.field = field;
        }

        @Override
        public Function<Throwable, ?> getFunction() {
            return field;
        }
    }

    /**
     * Holds onto a {@link Function} that takes a {@link Throwable} and
     * returns a {@link PublisherBuilder}.
     * @param <T> the source value type
     */
    static final class HRSOnErrorResumeWith<T> implements Stage.OnErrorResumeWith, Function<Throwable, Graph> {

        private final Function<Throwable, ? extends PublisherBuilder<? extends T>> field;

        HRSOnErrorResumeWith(Function<Throwable, ? extends PublisherBuilder<? extends T>> field) {
            this.field = field;
        }

        @Override
        public Graph apply(Throwable o) {
            return getGraph(field.apply(o));
        }

        @Override
        public Function<Throwable, Graph> getFunction() {
            return this;
        }
    }

    /**
     * Holds onto a {@link Function} that takes a {@link Throwable} and
     * returns a {@link Publisher}.
     * @param <T> the source value type
     */
    static final class HRSOnErrorResumeWithRs<T> implements Stage.OnErrorResumeWith, Function<Throwable, Graph> {

        private final Function<Throwable, ? extends Publisher<? extends T>> field;

        HRSOnErrorResumeWithRs(Function<Throwable, ? extends Publisher<? extends T>> field) {
            this.field = field;
        }

        @Override
        public Graph apply(Throwable o) {
            return new GraphCollection(Collections.singletonList(new HRSPublisher(field.apply(o))));
        }

        @Override
        public Function<Throwable, Graph> getFunction() {
            return this;
        }
    }
}
