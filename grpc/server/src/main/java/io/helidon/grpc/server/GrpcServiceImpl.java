package io.helidon.grpc.server;


import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
class GrpcServiceImpl
        implements GrpcService
    {
    private final ServerServiceDefinition serviceDefinition;

    GrpcServiceImpl(ServerServiceDefinition serviceDefinition)
        {
        this.serviceDefinition = serviceDefinition;
        }

    public ServerServiceDefinition bindService()
        {
        return serviceDefinition;
        }

    public void update(Methods methods)
        {
        // no-op
        }

    // ---- helpers ---------------------------------------------------------

    static <T> Supplier<T> createSupplier(Callable<T> callable)
        {
        return new CallableSupplier<>(callable);
        }

    static class CallableSupplier<T> implements Supplier<T>
        {
        private Callable<T> callable;

        CallableSupplier(Callable<T> callable)
            {
            this.callable = callable;
            }

        public T get()
            {
            try
                {
                return callable.call();
                }
            catch (Exception e)
                {
                throw new CompletionException(e.getMessage(), e);
                }
            }
        }

    static <T, U> BiConsumer<T, Throwable> completeWithResult(StreamObserver<U> observer)
        {
        return new CompletionAction<>(observer, true);
        }

    static <U> BiConsumer<Void, Throwable> completeWithoutResult(StreamObserver<U> observer)
        {
        return new CompletionAction<>(observer, false);
        }

    static class CompletionAction<T, U> implements BiConsumer<T, Throwable>
        {
        private StreamObserver<U> observer;
        private boolean fSendResult;

        CompletionAction(StreamObserver<U> observer, boolean fSendResult)
            {
            this.observer = observer;
            this.fSendResult = fSendResult;
            }

        @SuppressWarnings("unchecked")
        public void accept(T result, Throwable error)
            {
            if (error != null)
                {
                observer.onError(error);
                }
            else
                {
                if (fSendResult)
                    {
                    observer.onNext((U) result);
                    }
                observer.onCompleted();
                }
            }

        }
    }
