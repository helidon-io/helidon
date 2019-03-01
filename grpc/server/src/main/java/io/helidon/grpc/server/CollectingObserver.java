package io.helidon.grpc.server;


import io.grpc.stub.StreamObserver;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;


/**
 * @author Aleksandar Seovic  2019.03.01
 */
public class CollectingObserver<T, V, U, A, R> implements StreamObserver<V>
    {
    private final Collector<T, A, R> collector;
    private final StreamObserver<U> responseObserver;
    private final Function<V, T> requestConverter;
    private final Function<R, U> responseConverter;
    private final Consumer<Throwable> errorHandler;

    private final A accumulator;

    public CollectingObserver(Collector<T, A, R> collector,
                              StreamObserver<U> responseObserver)
        {
        this(collector, responseObserver, null, null, null);
        }

    public CollectingObserver(Collector<T, A, R> collector,
                              StreamObserver<U> responseObserver,
                              Consumer<Throwable> errorHandler)
        {
        this(collector, responseObserver, null, null, errorHandler);
        }

    public CollectingObserver(Collector<T, A, R> collector,
                              StreamObserver<U> responseObserver,
                              Function<V, T> requestConverter,
                              Function<R, U> responseConverter)
        {
        this(collector, responseObserver, requestConverter, responseConverter, null);
        }

    @SuppressWarnings("unchecked")
    public CollectingObserver(Collector<T, A, R> collector,
                              StreamObserver<U> responseObserver,
                              Function<V, T> requestConverter,
                              Function<R, U> responseConverter,
                              Consumer<Throwable> errorHandler)
        {
        this.collector = collector;
        this.responseObserver = responseObserver;
        this.requestConverter = Optional.ofNullable(requestConverter).orElse(v -> (T) v);
        this.responseConverter = Optional.ofNullable(responseConverter).orElse(r -> (U) r);
        this.errorHandler = Optional.ofNullable(errorHandler).orElse(t -> {});
        this.accumulator = collector.supplier().get();
        }

    public void onNext(V value)
        {
        collector.accumulator().accept(accumulator, requestConverter.apply(value));
        }

    public void onError(Throwable t)
        {
        errorHandler.accept(t);
        }

    public void onCompleted()
        {
        R result = collector.finisher().apply(accumulator);
        responseObserver.onNext(responseConverter.apply(result));
        responseObserver.onCompleted();
        }
    }
