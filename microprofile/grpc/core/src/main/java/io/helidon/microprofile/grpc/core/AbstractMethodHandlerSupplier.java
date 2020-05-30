/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.grpc.core.GrpcHelper;
import io.helidon.grpc.core.MethodHandler;
import io.helidon.grpc.core.SafeStreamObserver;

import com.google.protobuf.Empty;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * A base class for {@link MethodHandlerSupplier} implementations.
 */
abstract class AbstractMethodHandlerSupplier
        implements MethodHandlerSupplier {

    private final MethodDescriptor.MethodType methodType;

    /**
     * Create an {@link AbstractMethodHandlerSupplier}.
     *
     * @param methodType  the {@link MethodDescriptor.MethodType} to handle
     * @throws java.lang.NullPointerException if the method type parameter is {@code null}
     */
    AbstractMethodHandlerSupplier(MethodDescriptor.MethodType methodType) {
        this.methodType = Objects.requireNonNull(methodType, "The method type parameter cannot be null");
    }

    @Override
    public boolean supplies(AnnotatedMethod method) {
        return isRequiredMethodType(method);
    }

    /**
     * Determine whether the specified method is annotated with {@link GrpcMethod}
     * or another annotation that is itself annotated with {@link GrpcMethod}
     * with a type matching this handler's {@link #methodType}.
     *
     * @param method  the method to test
     * @return  {@code true} if the method is annotated with the correct type
     */
    boolean isRequiredMethodType(AnnotatedMethod method) {
        if (method == null) {
            return false;
        }

        GrpcMethod annotation = method.firstAnnotationOrMetaAnnotation(GrpcMethod.class);
        return annotation != null && methodType.equals(annotation.type());
    }

    /**
     * A base class for method handlers.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    public abstract static class AbstractHandler<ReqT, RespT>
            implements MethodHandler<ReqT, RespT> {

        private final String methodName;
        private final AnnotatedMethod method;
        private final Supplier<?> instance;
        private final MethodDescriptor.MethodType methodType;
        private Class<?> requestType = Empty.class;
        private Class<?> responseType = Empty.class;

        /**
         * Create a handler.
         *
         * @param methodName the name of the gRPC method
         * @param method   the underlying handler method this handler should call
         * @param instance the supplier to use to obtain the object to call the method on
         * @param methodType the type of method handled by this handler
         */
        protected AbstractHandler(String methodName,
                                  AnnotatedMethod method,
                                  Supplier<?> instance,
                                  MethodDescriptor.MethodType methodType) {
            this.methodName = methodName;
            this.method = method;
            this.instance = instance;
            this.methodType = methodType;
        }

        @Override
        public final MethodDescriptor.MethodType type() {
            return methodType;
        }

        @Override
        public void invoke(ReqT request, StreamObserver<RespT> observer) {
            StreamObserver<RespT> safe = SafeStreamObserver.ensureSafeObserver(observer);

            if (Empty.class.equals(requestType)) {
                safe = new NullHandlingResponseObserver<>(observer);
            }

            try {
                invoke(method.declaredMethod(), instance.get(), request, safe);
            } catch (Throwable thrown) {
                safe.onError(GrpcHelper.ensureStatusException(thrown, Status.INTERNAL));
            }
        }

        /**
         * Invoke the actual unary or server streaming gRPC method handler.
         *
         * @param method    the {@link Method} to invoke
         * @param instance  the service instance to invoke the method on
         * @param request   the method request
         * @param observer  the method response observer
         * @throws InvocationTargetException if an error occurs invoking the method
         * @throws IllegalAccessException    if the method cannot be accessed
         */
        protected abstract void invoke(Method method, Object instance, ReqT request, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException;

        @Override
        public StreamObserver<ReqT> invoke(StreamObserver<RespT> observer) {
            StreamObserver<RespT> safe = SafeStreamObserver.ensureSafeObserver(observer);
            try {
                return invoke(method.declaredMethod(), instance.get(), safe);
            } catch (Throwable thrown) {
                throw GrpcHelper.ensureStatusRuntimeException(thrown, Status.INTERNAL);
            }
        }

        /**
         * Invoke the actual client streaming or bi-directional gRPC method handler.
         *
         * @param method    the {@link Method} to invoke
         * @param instance  the service instance to invoke the method on
         * @param observer  the method response observer
         * @return  the {@link StreamObserver} to receive requests from the client
         * @throws InvocationTargetException if an error occurs invoking the method
         * @throws IllegalAccessException    if the method cannot be accessed
         */
        protected abstract StreamObserver<ReqT> invoke(Method method, Object instance, StreamObserver<RespT> observer)
                throws InvocationTargetException, IllegalAccessException;

        @Override
        public Class<?> getRequestType() {
            RequestType annotation = method.getAnnotation(RequestType.class);
            if (annotation != null) {
                return annotation.value();
            }
            return requestType;
        }

        /**
         * Set the request type to use if no {@link RequestType} annotation
         * is present on the annotated method.
         *
         * @param requestType  the request type
         */
        protected void setRequestType(Class<?> requestType) {
            this.requestType = requestType;
        }

        @Override
        public Class<?> getResponseType() {
            ResponseType annotation = method.getAnnotation(ResponseType.class);
            if (annotation != null) {
                return annotation.value();
            }
            return responseType;
        }

        @Override
        public String javaMethodName() {
            return method.declaredMethod().getName();
        }

        /**
         * Set the response type to use if no {@link ResponseType} annotation
         * is present on the annotated method.
         * @param responseType  the response type
         */
        protected void setResponseType(Class<?> responseType) {
            this.responseType = responseType;
        }

        /**
         * Obtain the gRPC method name.
         *
         * @return the gRPC method name
         */
        protected String methodName() {
            return methodName;
        }

        /**
         * Complete a {@link io.grpc.stub.StreamObserver}.
         *
         * @param response  the response value
         * @param thrown    an error that may have occurred
         * @param observer  the {@link io.grpc.stub.StreamObserver} to complete
         * @return always returns {@link Void} (i.e. {@code null})
         */
        protected Void handleFuture(RespT response, Throwable thrown, StreamObserver<RespT> observer) {
            if (thrown == null) {
                if (response != null) {
                    observer.onNext(response);
                }
                observer.onCompleted();
            } else {
                observer.onError(GrpcHelper.ensureStatusException(thrown, Status.INTERNAL));
            }
            return null;
        }

        /**
         * Obtain the generic type of a {@link java.lang.reflect.Type}
         * <p>
         * Typically used to obtain the generic type of a
         * {@link io.grpc.stub.StreamObserver} but could
         * be used to obtain the generic type of other
         * classes.
         * <p>
         * If the type passed in is a {@link Class} then it has no generic
         * component so the Object Class will be returned. Typically this
         * would be due to a declaration such as
         * <pre>StreamObserver observer</pre> instead of a generic declaration
         * such as <pre>StreamObserver&lt;String&gt; observer</pre>.
         *
         * @param type  the type to obtain the generic type from
         * @return the generic type of a {@link java.lang.reflect.Type}
         */
        protected Class<?> getGenericResponseType(Type type) {
            if (type instanceof Class) {
                return Object.class;
            } else {
                return ModelHelper.getGenericType(type);
            }
        }
    }

    /**
     * A response that handles null values.
     *
     * @param <V> the type of the response
     */
    private static class NullHandlingResponseObserver<V>
            implements StreamObserver<V> {

        private final StreamObserver delegate;

        private NullHandlingResponseObserver(StreamObserver<V> delegate) {
            this.delegate = delegate;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onNext(V value) {
            if (value == null) {
                delegate.onNext(Empty.getDefaultInstance());
            }
            delegate.onNext(value);
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onCompleted() {
            delegate.onCompleted();
        }
    }
}
