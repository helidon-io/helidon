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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import io.helidon.grpc.core.GrpcHelper;
import io.helidon.grpc.core.MethodHandler;

import com.google.protobuf.Empty;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * A supplier of {@link io.helidon.grpc.core.MethodHandler}s for unary gRPC methods.
 */
public class UnaryMethodHandlerSupplier
        extends AbstractMethodHandlerSupplier {

    /**
     * Create a supplier of handlers for server streaming methods.
     */
    // method is public because it is loaded via ServiceLoader
    public UnaryMethodHandlerSupplier() {
        super(MethodDescriptor.MethodType.UNARY);
    }

    @Override
    public boolean supplies(AnnotatedMethod method) {
        return super.supplies(method) && determineCallType(method) != CallType.unknown;
    }

    @Override
    public <ReqT, RespT> MethodHandler<ReqT, RespT> get(String methodName, AnnotatedMethod method, Supplier<?> instance) {
        if (!isRequiredMethodType(method)) {
            throw new IllegalArgumentException("Method not annotated as a unary method: " + method);
        }

        CallType type = determineCallType(method);
        MethodHandler<ReqT, RespT> handler;

        switch (type) {
        case requestResponse:
            handler = new RequestResponse<>(methodName, method, instance);
            break;
        case responseOnly:
            handler = new ResponseOnly<>(methodName, method, instance);
            break;
        case requestNoResponse:
            handler = new RequestNoResponse<>(methodName, method, instance);
            break;
        case noRequestNoResponse:
            handler = new NoRequestNoResponse<>(methodName, method, instance);
            break;
        case futureResponse:
            handler = new FutureResponse<>(methodName, method, instance);
            break;
        case futureResponseNoRequest:
            handler = new FutureResponseNoRequest<>(methodName, method, instance);
            break;
        case unary:
            handler = new Unary<>(methodName, method, instance);
            break;
        case unaryRequest:
            handler = new UnaryNoRequest<>(methodName, method, instance);
            break;
        case unaryFuture:
            handler = new UnaryFuture<>(methodName, method, instance);
            break;
        case unaryFutureNoRequest:
            handler = new UnaryFutureNoRequest<>(methodName, method, instance);
            break;
        case unknown:
        default:
            throw new IllegalArgumentException("Not a supported unary method signature: " + method);
        }
        return handler;
    }

    /**
     * Determine the type of unary method by analyzing the method signature.
     *
     * @param method  the method to analyze
     * @return the {@link CallType} of the method
     */
    private CallType determineCallType(AnnotatedMethod method) {
        Type[] parameterTypes = method.parameterTypes();
        int paramCount = parameterTypes.length;
        Type returnType = method.returnType();
        boolean voidReturn = void.class.equals(returnType);
        CallType callType;

        if (paramCount == 2) {
            if (StreamObserver.class.equals(parameterTypes[1]) && voidReturn) {
                // Assume that the first parameter is the request value
                // Signature is void invoke(ReqT, StreamObserver<ResT>)
                callType = CallType.unary;
            } else if (CompletableFuture.class.equals(parameterTypes[1]) && voidReturn) {
                // Assume that the first parameter is the request value
                // Signature is void invoke(ReqT, CompletableFuture<ResT>)
                callType = CallType.unaryFuture;
            } else {
                // Signature is unsupported - <?> invoke(<?>, <?>)
                callType = CallType.unknown;
            }
        } else if (paramCount == 1) {
            if (voidReturn) {
                if (StreamObserver.class.equals(parameterTypes[0])) {
                    // The single parameter is a StreamObserver so assume it is for the response
                    // Signature is void invoke(StreamObserver<ResT>)
                    callType = CallType.unaryRequest;
                } else if (CompletableFuture.class.equals(parameterTypes[0])) {
                    // The single parameter is a CompletableFuture so assume it is for the response
                    // Signature is void invoke(CompletableFuture<ResT>)
                    callType = CallType.unaryFutureNoRequest;
                } else {
                    // Assume that the single parameter is the request value and there is no response
                    // Signature is void invoke(ReqT)
                    callType = CallType.requestNoResponse;
                }
            } else {
                if (CompletableFuture.class.equals(returnType)) {
                    // Assume that the single parameter is the request value and the response is a CompletableFuture
                    // Signature is CompletableFuture<ResT> invoke(ReqT)
                    callType = CallType.futureResponse;
                } else if (CompletionStage.class.equals(returnType)) {
                    // Assume that the single parameter is the request value and the response is a CompletionStage
                    // Signature is CompletionStage<ResT> invoke(ReqT)
                    callType = CallType.futureResponse;
                } else {
                    // Assume that the single parameter is the request value
                    // and that the return is the response value
                    // Signature is ResT invoke(ReqT)
                    callType = CallType.requestResponse;
                }
            }
        } else if (paramCount == 0) {
            if (CompletableFuture.class.equals(returnType)) {
                // There is no request parameter the response is a CompletableFuture
                // Signature is CompletableFuture<ResT> invoke()
                callType = CallType.futureResponseNoRequest;
            } else if (CompletionStage.class.equals(returnType)) {
                // There is no request parameter the response is a CompletionStage
                // Signature is CompletionStage<ResT> invoke()
                callType = CallType.futureResponseNoRequest;
            } else if (voidReturn) {
                // There is no request parameter and no response
                // Signature is void invoke()
                callType = CallType.noRequestNoResponse;
            } else {
                // There is no request parameter only a response
                // Signature is ResT invoke()
                callType = CallType.responseOnly;
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
     * of unary method signatures.
     */
    private enum CallType {
        /**
         * A unary call with a request and response.
         * <pre>
         *     RestT invoke(ReqT request)
         * </pre>
         */
        requestResponse,
        /**
         * A unary call with no request only a response.
         * <pre>
         *     RestT invoke()
         * </pre>
         */
        responseOnly,
        /**
         * A unary call with a request but no response.
         * <pre>
         *     void invoke(ReqT request)
         * </pre>
         */
        requestNoResponse,
        /**
         * A unary call with no request and no response.
         * <pre>
         *     void invoke()
         * </pre>
         */
        noRequestNoResponse,
        /**
         * An unary call with a {@link CompletionStage} response.
         * <pre>
         *     CompletionStage&ltResT&gt; invoke(ReqT request)
         * </pre>
         */
        futureResponse,
        /**
         * An unary call with no request and a {@link CompletionStage} response.
         * <pre>
         *     CompletionStage&ltResT&gt; invoke()
         * </pre>
         */
        futureResponseNoRequest,
        /**
         * An standard unary call.
         * <pre>
         *     void invoke(ReqT request, StreamObserver&lt;RespT&gt; observer)
         * </pre>
         */
        unary,
        /**
         * An standard unary call with no request.
         * <pre>
         *     void invoke(StreamObserver&lt;RespT&gt; observer)
         * </pre>
         */
        unaryRequest,
        /**
         * An standard unary call with a {@link CompletableFuture} in place of
         * a {@link StreamObserver}.
         * <pre>
         *     void invoke(ReqT request, CompletableFuture&lt;RespT&gt; observer)
         * </pre>
         */
        unaryFuture,
        /**
         * An standard unary call without an request and with a {@link CompletableFuture}
         * in place of a {@link StreamObserver}.
         * <pre>
         *     void invoke(CompletableFuture&lt;RespT&gt; observer)
         * </pre>
         */
        unaryFutureNoRequest,
        /**
         * A call type not recognised by this supplier.
         */
        unknown
    }

    // ----- call handler inner classes -------------------------------------

    /**
     * A base class for unary method handlers.
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public abstract static class AbstractUnaryHandler<ReqT, RespT>
            extends AbstractHandler<ReqT, RespT> {

        /**
         * The argument to use for a {@code null} request parameter.
         */
        static final Empty EMPTY = Empty.getDefaultInstance();

        AbstractUnaryHandler(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance, MethodDescriptor.MethodType.UNARY);
        }

        @Override
        protected StreamObserver<ReqT> invoke(Method method, Object instance, StreamObserver<RespT> observer) {
            throw Status.UNIMPLEMENTED.asRuntimeException();
        }

        Object invokeUnary(Object request, UnaryClient client) {
            try {
                return invokeUnaryAsync(request, client)
                        .toCompletableFuture()
                        .get();
            } catch (Throwable thrown) {
                throw GrpcHelper.ensureStatusRuntimeException(thrown, Status.INTERNAL);
            }
        }

        CompletionStage<Object> invokeUnaryAsync(Object request, UnaryClient client) {
            try {
                return client.unary(methodName(), request);
            } catch (Throwable thrown) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(GrpcHelper.ensureStatusRuntimeException(thrown, Status.INTERNAL));
                return future;
            }
        }

        void invokeUnary(Object request, StreamObserver<Object> observer, UnaryClient client) {
            try {
                invokeUnaryAsync(request, client)
                        .handle((response, error) -> {
                            if (error == null) {
                                observer.onNext(response);
                                observer.onCompleted();
                            } else {
                                observer.onError(error);
                            }
                            return null;
                        });
            } catch (Throwable thrown) {
                observer.onError(GrpcHelper.ensureStatusRuntimeException(thrown, Status.INTERNAL));
            }
        }

        void invokeUnaryAsync(Object request, CompletableFuture<Object> future, UnaryClient client) {
            try {
                invokeUnaryAsync(request, client)
                        .handle((response, error) -> {
                            if (error == null) {
                                future.complete(response);
                            } else {
                                future.completeExceptionally(error);
                            }
                            return null;
                        });
            } catch (Throwable thrown) {
                future.completeExceptionally(GrpcHelper.ensureStatusRuntimeException(thrown, Status.INTERNAL));
            }
        }
    }

    // ----- RequestResponse call handler -----------------------------------

    /**
     * A unary {@link MethodHandler} that calls a handler method of the form.
     * <pre>
     *     RestT invoke(ReqT request)
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class RequestResponse<ReqT, RespT>
            extends AbstractUnaryHandler<ReqT, RespT> {

        RequestResponse(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setRequestType(method.parameterTypes()[0]);
            setResponseType(method.returnType());
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            RespT response = (RespT) method.invoke(instance, request);
            observer.onNext(response);
            observer.onCompleted();
        }

        /**
         * Invoke the client call.
         * <p>
         * The call is from a method signature:
         * <pre>
         *     RestT invoke(ReqT request);
         * </pre>
         * so the request is in {@code args[0]}.
         *
         * @param args the call arguments.
         * @param client the {@link UnaryClient} instance to forward the call to
         *
         * @return the request response
         */
        @Override
        public Object unary(Object[] args, UnaryClient client) {
            return invokeUnary(args[0], client);
        }
    }

    // ----- ResponseOnly call handler --------------------------------------

    /**
     * A unary {@link MethodHandler} that calls a handler method of the form.
     * <pre>
     *     RestT invoke()
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class ResponseOnly<ReqT, RespT>
            extends AbstractUnaryHandler<ReqT, RespT> {

        ResponseOnly(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setResponseType(method.returnType());
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            RespT response = (RespT) method.invoke(instance);
            observer.onNext(response);
            observer.onCompleted();
        }

        /**
         * Invoke the client call.
         * <p>
         * The call is from a method signature:
         * <pre>
         *     RestT invoke();
         * </pre>
         * so there is no request parameter.
         *
         * @param args the call arguments.
         * @param client the {@link UnaryClient} instance to forward the call to
         *
         * @return the request response
         */
        @Override
        public Object unary(Object[] args, UnaryClient client) {
            // no request parameter, we cannot send null so we send Types.Empty
            return invokeUnary(EMPTY, client);
        }
    }

    // ----- RequestNoResponse call handler ---------------------------------

    /**
     * A unary {@link MethodHandler} that calls a handler method of the form.
     * <pre>
     *     void invoke(ReqT request)
     * </pre>
     * <p>
     * Because the underlying handler returns {@code void} the {@link StreamObserver#onNext(Object)}
     * method will not be called.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class RequestNoResponse<ReqT, RespT>
            extends AbstractUnaryHandler<ReqT, RespT> {

        RequestNoResponse(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setRequestType(method.parameterTypes()[0]);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            method.invoke(instance, request);
            observer.onNext((RespT) EMPTY);
            observer.onCompleted();
        }

        /**
         * Invoke the client call.
         * <p>
         * The call is from a method signature:
         * <pre>
         *     RestT invoke(ReqT request);
         * </pre>
         * so the request is in {@code args[0]}.
         *
         * @param args the call arguments.
         * @param client the {@link UnaryClient} instance to forward the call to
         *
         * @return the method signature return is {@code void} so this method
         *         always returns null
         */
        @Override
        public Object unary(Object[] args, UnaryClient client) {
            invokeUnary(args[0], client);
            return null;
        }
    }

    // ----- NoRequestNoResponse call handler -------------------------------

    /**
     * A unary {@link MethodHandler} that calls a handler method of the form.
     * <pre>
     *     void invoke()
     * </pre>
     * <p>
     * Because the underlying handler returns {@code void} the {@link StreamObserver#onNext(Object)}
     * method will not be called.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class NoRequestNoResponse<ReqT, RespT>
            extends AbstractUnaryHandler<ReqT, RespT> {

        NoRequestNoResponse(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            method.invoke(instance);
            observer.onNext((RespT) EMPTY);
            observer.onCompleted();
        }

        /**
         * Invoke the client call.
         * <p>
         * The call is from a method signature:
         * <pre>
         *     void invoke();
         * </pre>
         * so there is no request parameter.
         *
         * @param args the call arguments.
         * @param client the {@link UnaryClient} instance to forward the call to
         *
         * @return the method signature return is {@code void} so this method
         *         always returns null
         */
        @Override
        public Object unary(Object[] args, UnaryClient client) {
            invokeUnary(EMPTY, client);
            return null;
        }
    }

    // ----- FutureResponse call handler ------------------------------------

    /**
     * A unary {@link MethodHandler} that calls a handler method of the form.
     * <pre>
     *     CompletableFuture&lt;ResT&gt; invoke(ReqT request)
     * </pre>
     * <p>
     * If the future returned completes normally and has a none null none
     * {@link Void} result then that result will be passed to the
     * {@link StreamObserver#onNext(Object)} method.
     * If the future completes exceptionally then the error will be passed to
     * the {@link StreamObserver#onError(Throwable)} method.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class FutureResponse<ReqT, RespT>
            extends AbstractUnaryHandler<ReqT, RespT> {

        FutureResponse(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setRequestType(method.parameterTypes()[0]);
            setResponseType(getGenericResponseType(method.genericReturnType()));
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            CompletableFuture<RespT> future = (CompletableFuture<RespT>) method.invoke(instance, request);
            future.handle((response, thrown) -> handleFuture(response, thrown, observer));
        }

        /**
         * Invoke the client call.
         * <p>
         * The call is from a method signature:
         * <pre>
         *     CompletableFuture&lt;ResT&gt; invoke(ReqT request)
         * </pre>
         * so the request parameter is in {@code args[0]}.
         *
         * @param args the call arguments.
         * @param client the {@link UnaryClient} instance to forward the call to
         *
         * @return the method signature return is {@code void} so this method
         *         always returns null
         */
        @Override
        public Object unary(Object[] args, UnaryClient client) {
            return invokeUnaryAsync(args[0], client);
        }
    }

    // ----- FutureResponseNoRequest call handler ---------------------------

    /**
     * A unary {@link MethodHandler} that calls a handler method of the form.
     * <pre>
     *     CompletableFuture&lt;ResT&gt; invoke()
     * </pre>
     * <p>
     * If the future returned completes normally and has a none null none
     * {@link Void} result then that result will be passed to the
     * {@link StreamObserver#onNext(Object)} method.
     * If the future completes exceptionally then the error will be passed to
     * the {@link StreamObserver#onError(Throwable)} method.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class FutureResponseNoRequest<ReqT, RespT>
            extends AbstractUnaryHandler<ReqT, RespT> {

        FutureResponseNoRequest(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setResponseType(getGenericResponseType(method.genericReturnType()));
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            CompletableFuture<RespT> future = (CompletableFuture<RespT>) method.invoke(instance);
            future.handle((response, thrown) -> handleFuture(response, thrown, observer));
        }

        /**
         * Invoke the client call.
         * <p>
         * The call is from a method signature:
         * <pre>
         *     CompletableFuture&lt;ResT&gt; invoke()
         * </pre>
         * so there is no request parameter.
         *
         * @param args the call arguments.
         * @param client the {@link UnaryClient} instance to forward the call to
         *
         * @return the method signature return is {@code void} so this method
         *         always returns null
         */
        @Override
        public Object unary(Object[] args, UnaryClient client) {
            return invokeUnaryAsync(EMPTY, client);
        }
    }

    // ----- Unary call handler ---------------------------------------------

    /**
     * A unary {@link MethodHandler} that calls a standard unary method handler
     * method of the form.
     * <pre>
     *     void invoke(ReqT request, StreamObserver&lt;RespT&gt; observer)
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class Unary<ReqT, RespT>
            extends AbstractUnaryHandler<ReqT, RespT> {

        Unary(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setRequestType(method.parameterTypes()[0]);
            setResponseType(getGenericResponseType(method.genericParameterTypes()[1]));
        }

        @Override
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            method.invoke(instance, request, observer);
        }

        /**
         * Invoke the client call.
         * <p>
         * The call is from a method signature:
         * <pre>
         *     void invoke(ReqT request, StreamObserver&lt;RespT&gt; observer)
         * </pre>
         * so the request parameter is in {@code args[0]} and the {@link StreamObserver}
         * to receive the response is in {@code args[1}.
         *
         * @param args the call arguments.
         * @param client the {@link UnaryClient} instance to forward the call to
         *
         * @return the method signature return is {@code void} so this method
         *         always returns null
         */
        @Override
        @SuppressWarnings("unchecked")
        public Object unary(Object[] args, UnaryClient client) {
            invokeUnary(args[0], (StreamObserver<Object>) args[1], client);
            return null;
        }
    }

    // ----- UnaryNoRequest call handler ------------------------------------

    /**
     * A unary {@link MethodHandler} that calls a unary method handler method
     * of the form.
     * <pre>
     *     void invoke(StreamObserver&lt;RespT&gt; observer)
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class UnaryNoRequest<ReqT, RespT>
            extends AbstractUnaryHandler<ReqT, RespT> {

        UnaryNoRequest(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setResponseType(getGenericResponseType(method.genericParameterTypes()[0]));
        }

        @Override
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            method.invoke(instance, observer);
        }

        /**
         * Invoke the client call.
         * <p>
         * The call is from a method signature:
         * <pre>
         *     void invoke(StreamObserver&lt;RespT&gt; observer)
         * </pre>
         * so there is no request parameter and the {@link StreamObserver}
         * to receive the response is in {@code args[0}.
         *
         * @param args the call arguments.
         * @param client the {@link UnaryClient} instance to forward the call to
         *
         * @return the method signature return is {@code void} so this method
         *         always returns null
         */
        @Override
        @SuppressWarnings("unchecked")
        public Object unary(Object[] args, UnaryClient client) {
            invokeUnary(EMPTY, (StreamObserver<Object>) args[0], client);
            return null;
        }
    }

    // ----- UnaryFuture call handler ---------------------------------------

    /**
     * A unary {@link MethodHandler} that calls a handler method of the form.
     * <pre>
     *     void invoke(ReqT request, CompletableFuture&lt;RespT&gt; future)
     * </pre>
     * <p>
     * If the future completes normally and has a none null none {@link Void}
     * result then that result will be passed to the
     * {@link StreamObserver#onNext(Object)} method.
     * If the future completes exceptionally then the error will be passed to
     * the {@link StreamObserver#onError(Throwable)} method.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class UnaryFuture<ReqT, RespT>
            extends AbstractUnaryHandler<ReqT, RespT> {

        UnaryFuture(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setRequestType(method.parameterTypes()[0]);
            setResponseType(getGenericResponseType(method.genericParameterTypes()[1]));
        }

        @Override
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            CompletableFuture<RespT> future = new CompletableFuture<>();
            future.handleAsync((response, thrown) -> handleFuture(response, thrown, observer));
            method.invoke(instance, request, future);
        }

        /**
         * Invoke the client call.
         * <p>
         * The call is from a method signature:
         * <pre>
         *     void invoke(ReqT request, CompletableFuture&lt;RespT&gt; future)
         * </pre>
         * so the request parameter is in {@code args[0]} and the {@link CompletableFuture}
         * to receive the response is in {@code args[1}.
         *
         * @param args the call arguments.
         * @param client the {@link UnaryClient} instance to forward the call to
         *
         * @return the method signature return is {@code void} so this method
         *         always returns null
         */
        @Override
        @SuppressWarnings("unchecked")
        public Object unary(Object[] args, UnaryClient client) {
            invokeUnaryAsync(args[0], (CompletableFuture<Object>) args[1], client);
            return null;
        }
    }

    // ----- UnaryFutureNoRequest call handler ------------------------------

    /**
     * A unary {@link MethodHandler} that calls a handler method of the form.
     * <pre>
     *     void invoke(CompletableFuture&lt;RespT&gt; future)
     * </pre>
     * <p>
     * If the future completes normally and has a none null none {@link Void}
     * result then that result will be passed to the
     * {@link StreamObserver#onNext(Object)} method.
     * If the future completes exceptionally then the error will be passed to
     * the {@link StreamObserver#onError(Throwable)} method.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class UnaryFutureNoRequest<ReqT, RespT>
            extends AbstractUnaryHandler<ReqT, RespT> {

        UnaryFutureNoRequest(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance);
            setResponseType(getGenericResponseType(method.genericParameterTypes()[0]));
        }

        @Override
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException {
            CompletableFuture<RespT> future = new CompletableFuture<>();
            future.handleAsync((response, thrown) -> handleFuture(response, thrown, observer));
            method.invoke(instance, future);
        }

        /**
         * Invoke the client call.
         * <p>
         * The call is from a method signature:
         * <pre>
         *     void invoke(CompletableFuture&lt;RespT&gt; future)
         * </pre>
         * so there is no request parameter and the {@link CompletableFuture}
         * to receive the response is in {@code args[1}.
         *
         * @param args the call arguments.
         * @param client the {@link UnaryClient} instance to forward the call to
         *
         * @return the method signature return is {@code void} so this method
         *         always returns null
         */
        @Override
        @SuppressWarnings("unchecked")
        public Object unary(Object[] args, UnaryClient client) {
            invokeUnaryAsync(EMPTY, (CompletableFuture<Object>) args[0], client);
            return null;
        }
    }
}
