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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.grpc.core.GrpcHelper;
import io.helidon.grpc.core.MethodHandler;

import com.google.protobuf.Empty;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import static io.helidon.grpc.core.ResponseHelper.stream;

/**
 * A supplier of {@link io.helidon.grpc.core.MethodHandler}s for server streaming gRPC methods.
 */
public class ServerStreamingMethodHandlerSupplier
        extends AbstractMethodHandlerSupplier {

    /**
     * Create a supplier of handlers for server streaming methods.
     */
    // method is public because it is loaded via ServiceLoader
    public ServerStreamingMethodHandlerSupplier() {
        super(MethodDescriptor.MethodType.SERVER_STREAMING);
    }

    @Override
    public boolean supplies(AnnotatedMethod method) {
        return super.supplies(method) && determineCallType(method) != CallType.unknown;
    }

    @Override
    public <ReqT, RespT> MethodHandler<ReqT, RespT> get(String methodName, AnnotatedMethod method, Supplier<?> instance) {
        if (!isRequiredMethodType(method)) {
            throw new IllegalArgumentException("Method not annotated as a server streaming method: " + method);
        }

        CallType type = determineCallType(method);
        MethodHandler<ReqT, RespT> handler;

        switch (type) {
        case serverStreaming:
            handler = new ServerStreaming<>(methodName, method, instance);
            break;
        case serverStreamingNoRequest:
            handler = new ServerStreamingNoRequest<>(methodName, method, instance);
            break;
        case streamResponse:
            handler = new StreamResponse<>(methodName, method, instance);
            break;
        case streamResponseNoRequest:
            handler = new StreamResponseNoRequest<>(methodName, method, instance);
            break;
        case unknown:
        default:
            throw new IllegalArgumentException("Not a supported server streaming method signature: " + method);
        }
        return handler;
    }

    private CallType determineCallType(AnnotatedMethod method) {
        Type returnType = method.returnType();
        Type[] parameterTypes = method.parameterTypes();
        int paramCount = parameterTypes.length;
        boolean voidReturn = void.class.equals(returnType);
        CallType callType;

        if (paramCount == 2) {
            if (StreamObserver.class.equals(parameterTypes[1]) && voidReturn) {
                // Assume that the first parameter is the request value
                // Signature is void invoke(ReqT, StreamObserver<RespT>)
                callType = CallType.serverStreaming;
            } else {
                // Signature is unsupported - <?> invoke(<?>, <?>)
                callType = CallType.unknown;
            }
        } else if (paramCount == 1) {
            if (StreamObserver.class.equals(parameterTypes[0]) && voidReturn) {
                // Assume that the first parameter is the result observer and there is no request
                // Signature is void invoke(StreamObserver<RespT>)
                callType = CallType.serverStreamingNoRequest;
            } else if (Stream.class.equals(returnType)) {
                // Assume that the first parameter is the request value and the response is a Stream
                // Signature is Stream<RespT> invoke(ReqT)
                callType = CallType.streamResponse;
            } else {
                // Signature is unsupported - <?> invoke(<?>)
                callType = CallType.unknown;
            }
        } else if (paramCount == 0) {
            if (Stream.class.equals(returnType)) {
                // Assume that the there is no request value and the response is a Stream
                // Signature is Stream<RespT> invoke()
                callType = CallType.streamResponseNoRequest;
            } else {
                // Signature is unsupported - <?> invoke(<?>)
                callType = CallType.unknown;
            }
        } else {
            // Signature is unsupported - it has more than two parameters
            callType = CallType.unknown;
        }

        return callType;
    }

    // ----- CallType enumeration -------------------------------------------

    /**
     * An enumeration representing different supported types
     * of server streaming method signatures.
     */
    private enum CallType {
        /**
         * An standard server streaming call.
         * <pre>
         *     void invoke(ReqT request, StreamObserver&lt;RespT&gt; observer)
         * </pre>
         */
        serverStreaming,
        /**
         * A server streaming call that returns a {@link Stream} of responses.
         * <pre>
         *     Stream&lt;RespT&gt; invoke(ReqT request)
         * </pre>
         */
        streamResponse,
        /**
         * An server streaming call that takes no request parameter.
         * <pre>
         *     void invoke(StreamObserver&lt;RespT&gt; observer)
         * </pre>
         */
        serverStreamingNoRequest,
        /**
         * A server streaming call that takes no request parameter and
         * returns a {@link Stream} of responses.
         * <pre>
         *     Stream&lt;RespT&gt; invoke()
         * </pre>
         */
        streamResponseNoRequest,
        /**
         * A call type not recognised by this supplier.
         */
        unknown
    }

    // ----- call handler inner classes -------------------------------------

    /**
     * A base class for server streaming {@link MethodHandler}s.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public abstract static class AbstractServerStreamingHandler<ReqT, RespT>
            extends AbstractHandler<ReqT, RespT> {

        AbstractServerStreamingHandler(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance, MethodDescriptor.MethodType.SERVER_STREAMING);
        }

        @Override
        protected StreamObserver<ReqT> invoke(Method method, Object instance, StreamObserver<RespT> observer) {
            throw Status.UNIMPLEMENTED.asRuntimeException();
        }
    }

    // ----- ServerStreaming call handler -----------------------------------

    /**
     * A server streaming {@link MethodHandler} that calls a standard server
     * streaming method handler method of the form.
     * <pre>
     *     void invoke(ReqT request, StreamObserver&lt;RespT&gt; observer)
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class ServerStreaming<ReqT, RespT>
            extends AbstractServerStreamingHandler<ReqT, RespT> {

        ServerStreaming(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setRequestType(method.parameterTypes()[0]);
            setResponseType(getGenericResponseType(method.genericParameterTypes()[1]));
        }

        @Override
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            method.invoke(instance, request, observer);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object serverStreaming(Object[] args, ServerStreamingClient client) {
            try {
                client.serverStreaming(methodName(), args[0], (StreamObserver) args[1]);
                return null;
            } catch (Throwable thrown) {
                throw GrpcHelper.ensureStatusRuntimeException(thrown, Status.INTERNAL);
            }
        }
    }

    // ----- ServerStreamingNoRequest call handler --------------------------

    /**
     * A server streaming {@link MethodHandler} that calls a calls a server
     * streaming method handler method of the form.
     * <pre>
     *     void invoke(StreamObserver&lt;RespT&gt; observer)
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class ServerStreamingNoRequest<ReqT, RespT>
            extends AbstractServerStreamingHandler<ReqT, RespT> {

        ServerStreamingNoRequest(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setResponseType(getGenericResponseType(method.genericParameterTypes()[0]));
        }

        @Override
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            method.invoke(instance, observer);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object serverStreaming(Object[] args, ServerStreamingClient client) {
            try {
                client.serverStreaming(methodName(), Empty.getDefaultInstance(), (StreamObserver) args[0]);
                return null;
            } catch (Throwable thrown) {
                throw GrpcHelper.ensureStatusRuntimeException(thrown, Status.INTERNAL);
            }
        }
    }

    // ----- StreamResponse call handler ------------------------------------

    /**
     * A server streaming {@link MethodHandler} that calls a calls a server
     * streaming method handler method of the form.
     * <pre>
     *     Stream&lt;RespT&gt; invoke(ReqT request)
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class StreamResponse<ReqT, RespT>
            extends AbstractServerStreamingHandler<ReqT, RespT> {

        StreamResponse(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setRequestType(method.parameterTypes()[0]);
            setResponseType(getGenericResponseType(method.genericReturnType()));
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            Stream<RespT> stream = (Stream<RespT>) method.invoke(instance, request);
            stream(observer, stream);
        }

        @Override
        public Object serverStreaming(Object[] args, ServerStreamingClient client) {
            try {
                Observer<RespT> observer = new Observer<>();
                client.serverStreaming(methodName(), args[0], observer);
                return observer.future().get().stream();
            } catch (Throwable thrown) {
                throw GrpcHelper.ensureStatusRuntimeException(thrown, Status.INTERNAL);
            }
        }
    }

    // ----- StreamResponse call handler ------------------------------------

    /**
     * A server streaming {@link MethodHandler} that calls a calls a server
     * streaming method handler method of the form.
     * <pre>
     *     Stream&lt;RespT&gt; invoke()
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class StreamResponseNoRequest<ReqT, RespT>
            extends AbstractServerStreamingHandler<ReqT, RespT> {

        StreamResponseNoRequest(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setResponseType(getGenericResponseType(method.genericReturnType()));
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            Stream<RespT> stream = (Stream<RespT>) method.invoke(instance);
            stream(observer, stream);
        }

        @Override
        public Object serverStreaming(Object[] args, ServerStreamingClient client) {
            try {
                Observer<RespT> observer = new Observer<>();
                client.serverStreaming(methodName(), Empty.getDefaultInstance(), observer);
                return observer.future().get().stream();
            } catch (Throwable thrown) {
                throw GrpcHelper.ensureStatusRuntimeException(thrown, Status.INTERNAL);
            }
        }
    }

    /**
     * A {@link StreamObserver} that collects all of its responses into a
     * {@link List} and then completes a {@link CompletableFuture} when the
     * observer completes.
     *
     * @param <T>  the response type
     */
    private static class Observer<T>
            implements StreamObserver<T> {

        private CompletableFuture<List<T>> future = new CompletableFuture<>();
        private List<T> list = new ArrayList<>();

        private CompletableFuture<List<T>> future() {
            return future;
        }

        @Override
        public void onNext(T value) {
            list.add(value);
        }

        @Override
        public void onError(Throwable t) {
            future.completeExceptionally(t);
        }

        @Override
        public void onCompleted() {
            future.complete(list);
        }
    }
}
