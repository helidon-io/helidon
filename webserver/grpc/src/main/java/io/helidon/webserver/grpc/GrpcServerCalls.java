package io.helidon.webserver.grpc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.grpc.ServerCallHandler;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;

public final class GrpcServerCalls {
    private GrpcServerCalls() {
    }

    static <ReqT, ResT> ServerCallHandler<ReqT, ResT> unaryCall(Unary<ReqT, ResT> method) {
        return ServerCalls.asyncUnaryCall((request, responseObserver) -> {
            try {
                ResT response = method.invoke(request);
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } catch (Exception e) {
                responseObserver.onError(e);
            }
        });
    }

    static <ReqT, ResT> ServerCallHandler<ReqT, ResT> clientStream(ClientStream<ReqT, ResT> method, Duration timeout) {
        return ServerCalls.asyncClientStreamingCall(responseObserver -> {
            CompletableFuture<Collection<ReqT>> future = new CompletableFuture<>();

            future.orTimeout(timeout.getNano(), TimeUnit.NANOSECONDS)
                    .thenAccept(requests -> {
                        responseObserver.onNext(method.invoke(requests));
                        responseObserver.onCompleted();
                    })
                    .exceptionally(throwable -> {
                        responseObserver.onError(throwable);
                        return null;
                    });

            return new CollectingObserver<>(future);
        });
    }

    static <ReqT, ResT> ServerCallHandler<ReqT, ResT> serverStream(ServerStream<ReqT, ResT> method) {
        return ServerCalls.asyncServerStreamingCall((request, responseObserver) -> {
            try {
                Collection<ResT> response = method.invoke(request);
                for (ResT resT : response) {
                    responseObserver.onNext(resT);
                }
                responseObserver.onCompleted();
            } catch (Exception e) {
                responseObserver.onError(e);
            }
        });
    }

    static <ReqT, ResT> ServerCallHandler<ReqT, ResT> bidi(Bidi<ReqT, ResT> method, Duration timeout) {
        return ServerCalls.asyncBidiStreamingCall(responseObserver -> {
            CompletableFuture<Collection<ReqT>> future = new CompletableFuture<>();

            future.orTimeout(timeout.getNano(), TimeUnit.NANOSECONDS)
                    .thenAccept(requests -> {
                        Collection<ResT> response = method.invoke(requests);
                        response.forEach(responseObserver::onNext);
                        responseObserver.onCompleted();
                    })
                    .exceptionally(throwable -> {
                        responseObserver.onError(throwable);
                        return null;
                    });

            return new CollectingObserver<>(future);
        });
    }

    public interface Unary<ReqT, RespT> {
        RespT invoke(ReqT request);
    }

    public interface ServerStream<ReqT, RespT> {
        Collection<RespT> invoke(ReqT request);
    }

    public interface ClientStream<ReqT, RespT> {
        RespT invoke(Collection<ReqT> requests);
    }

    /**
     * Bidirectional streaming is by its design created for asynchronous communication.
     * This interface should be used only when you have a guarantee that the client sends all of its messages
     * and DOES NOT WAIT for the responses on each of them.
     * <p>
     * In case you need true asynchronous communication (e.g. clients sends a message, waits for server response,
     * send another one),
     * please use {@link io.grpc.stub.ServerCalls#asyncBidiStreamingCall(io.grpc.stub.ServerCalls.BidiStreamingMethod)}.
     *
     * @param <ReqT>  request type
     * @param <RespT> response type
     */
    public interface Bidi<ReqT, RespT> {
        Collection<RespT> invoke(Collection<ReqT> requests);
    }

    /**
     * Collects all elements (and possible exception) and completes the completable future when finished collecting.
     *
     * @param <T>
     */
    private static class CollectingObserver<T> implements StreamObserver<T> {
        private final List<T> collectedValues = new ArrayList<>();
        private final CompletableFuture<Collection<T>> future;

        private CollectingObserver(CompletableFuture<Collection<T>> future) {
            this.future = future;
        }

        @Override
        public void onNext(T value) {
            collectedValues.add(value);
        }

        @Override
        public void onError(Throwable t) {
            future.completeExceptionally(t);
        }

        @Override
        public void onCompleted() {
            future.complete(collectedValues);
        }
    }
}
