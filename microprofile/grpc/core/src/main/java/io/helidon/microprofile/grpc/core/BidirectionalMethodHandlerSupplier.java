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
import java.util.function.Supplier;

import io.helidon.grpc.core.GrpcHelper;
import io.helidon.grpc.core.MethodHandler;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * A supplier of {@link io.helidon.grpc.core.MethodHandler}s for bi-directional streaming gRPC methods.
 */
public class BidirectionalMethodHandlerSupplier
        extends AbstractMethodHandlerSupplier {

    /**
     * Create a supplier of handlers for bi-directional streaming methods.
     */
    // method is public because it is loaded via ServiceLoader
    public BidirectionalMethodHandlerSupplier() {
        super(MethodDescriptor.MethodType.BIDI_STREAMING);
    }

    @Override
    public boolean supplies(AnnotatedMethod method) {
        return super.supplies(method) && determineCallType(method) != CallType.unknown;
    }

    @Override
    public <ReqT, RespT> MethodHandler<ReqT, RespT> get(String methodName, AnnotatedMethod method, Supplier<?> instance) {
        if (!isRequiredMethodType(method)) {
            throw new IllegalArgumentException("Method not annotated as a bi-directional streaming method: " + method);
        }

        CallType type = determineCallType(method);
        MethodHandler<ReqT, RespT> handler;

        switch (type) {
        case bidiStreaming:
            handler = new BidiStreaming<>(methodName, method, instance);
            break;
        case unknown:
        default:
            throw new IllegalArgumentException("Not a supported bi-directional streaming method signature: " + method);
        }
        return handler;
    }

    private CallType determineCallType(AnnotatedMethod method) {
        Type returnType = method.returnType();
        CallType callType;

        Type[] parameterTypes = method.parameterTypes();
        int paramCount = parameterTypes.length;

        if (paramCount == 1) {
            if (StreamObserver.class.equals(parameterTypes[0])
                && StreamObserver.class.equals(returnType)) {
                // Assume that the first parameter is the response observer value
                // and the return is the request observer
                // Signature is StreamObserver<Reqt> invoke(StreamObserver<RespT>)
                callType = CallType.bidiStreaming;
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
     * of bi-directional streaming method signatures.
     */
    private enum CallType {
        /**
         * An standard bi-directional streaming call.
         * <pre>
         *     StreamObserver&lt;ReqT&gt; invoke(StreamObserver&lt;RespT&gt; observer)
         * </pre>
         */
        bidiStreaming,
        /**
         * A call type not recognised by this supplier.
         */
        unknown
    }

    // ----- call handler inner classes -------------------------------------

    /**
     * A base class for bi-directional streaming {@link MethodHandler}s.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public abstract static class AbstractServerStreamingHandler<ReqT, RespT>
            extends AbstractHandler<ReqT, RespT> {

        AbstractServerStreamingHandler(String methodName, AnnotatedMethod method, Supplier<?> instance) {
            super(methodName, method, instance, MethodDescriptor.MethodType.BIDI_STREAMING);
        }

        @Override
        protected void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer) {
            throw Status.UNIMPLEMENTED.asRuntimeException();
        }
    }

    // ----- BidiStreaming call handler -------------------------------------

    /**
     * A bi-directional streaming {@link MethodHandler} that
     * calls a standard bi-directional streaming method handler method of the form.
     * <pre>
     *     StreamObserver&lt;ReqT&gt; invoke(StreamObserver&lt;RespT&gt; observer)
     * </pre>
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public static class BidiStreaming<ReqT, RespT>
            extends AbstractServerStreamingHandler<ReqT, RespT> {

        BidiStreaming(String methodName, AnnotatedMethod method, Supplier<?> instance) {
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
        public Object bidirectional(Object[] args, BidirectionalClient client) {
            try {
                return client.bidiStreaming(methodName(), (StreamObserver<ReqT>) args[0]);
            } catch (Throwable thrown) {
                throw GrpcHelper.ensureStatusRuntimeException(thrown, Status.INTERNAL);
            }
        }
    }
}
