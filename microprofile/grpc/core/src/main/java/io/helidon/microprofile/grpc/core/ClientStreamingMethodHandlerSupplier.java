/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.grpc.core.MethodHandler;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import static io.helidon.grpc.core.ResponseHelper.completeAsync;

/**
 * A supplier of {@link io.helidon.grpc.core.MethodHandler}s for client streaming gRPC methods.
 */
public class ClientStreamingMethodHandlerSupplier
        extends AbstractMethodHandlerSupplier {

    /**
     * Create a supplier of handlers for client streaming methods.
     */
    // method is public because it is loaded via ServiceLoader
    public ClientStreamingMethodHandlerSupplier() {
        super(MethodDescriptor.MethodType.CLIENT_STREAMING);
    }

    @Override
    public boolean supplies(AnnotatedMethod method) {
        return super.supplies(method) && determineCallType(method) != CallType.unknown;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <ReqT, RespT> MethodHandler<ReqT, RespT> get(String methodName, AnnotatedMethod method, Supplier<?> instance) {
        if (!isRequiredMethodType(method)) {
            throw new IllegalArgumentException("Method not annotated as a client streaming method: " + method);
        }

        CallType type = determineCallType(method);
        MethodHandler handler;

        switch (type) {
        case clientStreaming:
            handler = new ClientStreaming<>(methodName, method, instance);
            break;
        case futureResponse:
            handler = new FutureResponse<>(methodName, method, instance);
            break;
        case clientStreamingIterable:
            handler = new ClientStreamingIterable(methodName, method, instance);
            break;
        case clientStreamingStream:
            handler = new ClientStreamingStream(methodName, method, instance);
            break;
        case unknown:
        default:
            throw new IllegalArgumentException("Not a supported client streaming method signature: " + method);
        }
        return handler;
    }

    private CallType determineCallType(AnnotatedMethod method) {
        Class<?> returnType = method.returnType();
        Class<?>[] parameterTypes = method.parameterTypes();
        int paramCount = parameterTypes.length;
        CallType callType;

        if (paramCount == 1) {
            if (StreamObserver.class.isAssignableFrom(parameterTypes[0])
                && StreamObserver.class.equals(returnType)) {
                // Assume that the first parameter is the response observer value
                // and the return is the request observer
                // Signature is StreamObserver<Reqt> invoke(StreamObserver<RespT>)
                callType = CallType.clientStreaming;
            } else if (Iterable.class.isAssignableFrom(parameterTypes[0])
                && CompletionStage.class.isAssignableFrom(returnType)) {
                // ** This is a client side only handler **
                // Assume that the first parameter is the requests to stream
                // and the return is the response
                // Signature is CompletionStage<RespT> invoke(Iterable<ReqT>)
                callType = CallType.clientStreamingIterable;
            } else if (Stream.class.isAssignableFrom(parameterTypes[0])
                && CompletionStage.class.isAssignableFrom(returnType)) {
                // ** This is a client side only handler **
                // Assume that the first parameter is the requests to stream
                // and the return is the response
                // Signature is CompletionStage<RespT> invoke(Stream<ReqT>)
                callType = CallType.clientStreamingStream;
            } else if (CompletionStage.class.isAssignableFrom(parameterTypes[0])
                && StreamObserver.class.equals(returnType)) {
                // Assume that the first parameter is the response CompletableStage value
                // and the return is the request observer
                // Signature is StreamObserver<Reqt> invoke(CompletableStage<RespT>)
                callType = CallType.futureResponse;
            } else {
                // Signature is unsupported - <?> invoke(<?>)
                callType = CallType.unknown;
            }
        } else {
            // Signature is unsupported
            callType = CallType.unknown;
        }

        return callType;
    }

    // ----- CallType enumeration -------------------------------------------

    /**
     * An enumeration representing different supported types
     * of client streaming method signatures.
     */
    private enum CallType {
        /**
         * A standard client streaming call.
         * <pre>
         *     StreamObserver&lt;ReqT&gt; invoke(StreamObserver&lt;RespT&gt; observer)
         * </pre>
         */
        clientStreaming,
        /**
         * A client side only client streaming call with an iterable request.
         * <pre>
         *     RespT invoke(Iterable&lt;ReqT&gt; requests)
         * </pre>
         */
        clientStreamingIterable,
        /**
         * A client side only client streaming call with an stream request.
         * <pre>
         *     RespT invoke(Stream&lt;ReqT&gt; requests)
         * </pre>
         */
        clientStreamingStream,
        /**
         * A standard client streaming call with an async response.
         * <pre>
         *     StreamObserver&lt;ReqT&gt; invoke(CompletionStage&lt;RespT&gt; future)
         * </pre>
         */
        futureResponse,
        /**
         * A call type not recognised by this supplier.
         */
        unknown
    }

    // ----- call handler inner classes -------------------------------------

    /**
     * A base class for client streaming {@link MethodHandler}s.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public abstract static class AbstractClientStreamingHandler<ReqT, RespT>
            extends AbstractHandler<ReqT, RespT> {

        AbstractClientStreamingHandler(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance, MethodDescriptor.MethodType.CLIENT_STREAMING);
        }

        @Override
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer) {
            throw Status.UNIMPLEMENTED.asRuntimeException();
        }
    }

    // ----- ClientStreaming call handler -----------------------------------

    /**
     * A client streaming {@link MethodHandler} that
     * calls a standard client streaming method handler method of the form.
     * <pre>
     *     StreamObserver&lt;ReqT&gt; invoke(StreamObserver&lt;RespT&gt; observer)
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class ClientStreaming<ReqT, RespT>
            extends AbstractClientStreamingHandler<ReqT, RespT> {

        ClientStreaming(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setRequestType(getGenericResponseType(method.genericReturnType()));
            setResponseType(getGenericResponseType(method.genericParameterTypes()[0]));
        }

        @Override
        @SuppressWarnings("unchecked")
        protected StreamObserver<ReqT> invoke(Method method, Object instance, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            return (StreamObserver<ReqT>) method.invoke(instance, observer);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object clientStreaming(Object[] args, ClientStreaming client) {
            return client.clientStreaming(methodName(), (StreamObserver) args[0]);
        }
    }

    // ----- FutureResponse call handler ------------------------------------

    /**
     * A client streaming {@link MethodHandler} that
     * calls a standard client streaming method handler method of the form.
     * <pre>
     *     StreamObserver&lt;ReqT&gt; invoke(CompletableFuture&lt;RespT&gt; future)
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class FutureResponse<ReqT, RespT>
            extends AbstractClientStreamingHandler<ReqT, RespT> {

        FutureResponse(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setRequestType(getGenericResponseType(method.genericReturnType()));
            setResponseType(getGenericResponseType(method.genericParameterTypes()[0]));
        }

        @Override
        @SuppressWarnings("unchecked")
        protected StreamObserver<ReqT> invoke(Method method, Object instance, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            CompletableFuture<RespT> future = new CompletableFuture<>();
            completeAsync(observer, future);
            return (StreamObserver<ReqT>) method.invoke(instance, future);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object clientStreaming(Object[] args, ClientStreaming client) {
            FutureObserver<RespT> observer = new FutureObserver<>((CompletableFuture<RespT>) args[0]);
            return client.clientStreaming(methodName(), observer);
        }
    }

    // ----- ClientStreamingIterable call handler ---------------------------

    /**
     * A client side only client streaming {@link MethodHandler} that
     * streams requests from an iterable.
     * <pre>
     *     CompletionStage&lt;RespT&gt; invoke(Iterable&lt;ReqT&gt; observer)
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class ClientStreamingIterable<ReqT, RespT>
            extends AbstractClientStreamingHandler<ReqT, RespT> {

        ClientStreamingIterable(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setRequestType(getGenericResponseType(method.genericReturnType()));
            setResponseType(getGenericResponseType(method.genericParameterTypes()[0]));
        }

        @Override
        public boolean clientOnly() {
            return true;
        }

        @Override
        protected StreamObserver<ReqT> invoke(Method method, Object instance, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            throw Status.UNIMPLEMENTED.asRuntimeException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object clientStreaming(Object[] args, ClientStreaming client) {
            try {
                CompletableFuture<Object> future = new CompletableFuture<>();
                StreamObserver<Object> responseObserver = new FutureObserver<>(future);
                Iterable<Object> iterable = (Iterable<Object>) args[0];
                StreamObserver<Object> requestObserver = client.clientStreaming(methodName(), responseObserver);

                for (Object value : iterable) {
                    requestObserver.onNext(value);
                }

                requestObserver.onCompleted();
                return future;
            } catch (Throwable thrown) {
                throw Status.INTERNAL.withCause(thrown).asRuntimeException();
            }
        }
    }


    // ----- ClientStreamingIterable call handler ---------------------------

    /**
     * A client side only client streaming {@link MethodHandler} that
     * streams requests from a stream.
     * <pre>
     *     CompletionStage&lt;RespT&gt; invoke(Stream&lt;ReqT&gt; observer)
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class ClientStreamingStream<ReqT, RespT>
            extends AbstractClientStreamingHandler<ReqT, RespT> {

        ClientStreamingStream(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setRequestType(getGenericResponseType(method.genericReturnType()));
            setResponseType(getGenericResponseType(method.genericParameterTypes()[0]));
        }

        @Override
        public boolean clientOnly() {
            return true;
        }

        @Override
        protected StreamObserver<ReqT> invoke(Method method, Object instance, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            throw Status.UNIMPLEMENTED.asRuntimeException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object clientStreaming(Object[] args, ClientStreaming client) {
            try {
                CompletableFuture<Object> future = new CompletableFuture<>();
                StreamObserver<Object> responseObserver = new FutureObserver<>(future);
                StreamObserver<Object> requestObserver = client.clientStreaming(methodName(), responseObserver);
                Stream<Object> stream = (Stream<Object>) args[0];

                stream.forEach(requestObserver::onNext);
                requestObserver.onCompleted();
                return future;
            } catch (Throwable thrown) {
                throw Status.INTERNAL.withCause(thrown).asRuntimeException();
            }
        }
    }


    /**
     * A {@link StreamObserver} that completes a {@link CompletableFuture}
     * with its received result.
     *
     * @param <T>  the result type
     */
    private static class FutureObserver<T>
            implements StreamObserver<T> {

        private CompletableFuture<T> future;
        private T value;

        private FutureObserver(CompletableFuture<T> future) {
            this.future = future;
        }

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            future.completeExceptionally(t);
        }

        @Override
        public void onCompleted() {
            future.complete(value);
        }
    }
}
