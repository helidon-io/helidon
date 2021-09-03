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

package io.helidon.grpc.client;

import java.util.Arrays;

import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.core.MarshallerSupplier;
import io.helidon.grpc.core.MethodHandler;
import io.helidon.grpc.core.PriorityBag;

import io.grpc.CallCredentials;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

/**
 * Encapsulates all metadata necessary to define a gRPC method. In addition to wrapping a {@link io.grpc.MethodDescriptor},
 * this class also holds the request and response types of the gRPC method. A
 * {@link io.helidon.grpc.client.ClientServiceDescriptor} can contain zero or more {@link io.grpc.MethodDescriptor}.
 *
 * An instance of ClientMethodDescriptor can be created either from an existing {@link io.grpc.MethodDescriptor} or
 * from one of the factory methods {@link #bidirectional(String, String)}, {@link #clientStreaming(String, String)},
 * {@link #serverStreaming(String, String)} or {@link #unary(String, String)}.
 */
public final class ClientMethodDescriptor {

    /**
     * The simple name of the method.
     */
    private String name;

    /**
     * The {@link io.grpc.MethodDescriptor} for this method. This is usually obtained from protocol buffer
     * method getDescriptor (from service getDescriptor).
     */
    private io.grpc.MethodDescriptor descriptor;

    /**
     * The list of client interceptors for this method.
     */
    private PriorityBag<ClientInterceptor> interceptors;

    /**
     * The {@link io.grpc.CallCredentials} for this method.
     */
    private CallCredentials callCredentials;

    /**
     * The method handler for this method.
     */
    private MethodHandler methodHandler;

    private ClientMethodDescriptor(String name,
                                   MethodDescriptor descriptor,
                                   PriorityBag<ClientInterceptor> interceptors,
                                   CallCredentials callCredentials,
                                   MethodHandler methodHandler) {
        this.name = name;
        this.descriptor = descriptor;
        this.interceptors = interceptors;
        this.callCredentials = callCredentials;
        this.methodHandler = methodHandler;
    }

    /**
     * Creates a new {@link ClientMethodDescriptor.Builder} with the specified name and {@link io.grpc.MethodDescriptor}.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the simple method name
     * @param descriptor  the underlying gRPC {@link io.grpc.MethodDescriptor.Builder}
     * @return A new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static Builder builder(String serviceName,
                           String name,
                           io.grpc.MethodDescriptor.Builder descriptor) {
        return new Builder(serviceName, name, descriptor);
    }

    /**
     * Creates a new {@link ClientMethodDescriptor.Builder} with the specified
     * name and {@link io.grpc.MethodDescriptor}.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the simple method name
     * @param descriptor  the underlying gRPC {@link io.grpc.MethodDescriptor.Builder}
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static ClientMethodDescriptor create(String serviceName,
                                         String name,
                                         io.grpc.MethodDescriptor.Builder descriptor) {
        return builder(serviceName, name, descriptor).build();
    }

    /**
     * Creates a new unary {@link ClientMethodDescriptor.Builder} with
     * the specified name.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the method name
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static ClientMethodDescriptor.Builder unary(String serviceName, String name) {
        return builder(serviceName, name, MethodDescriptor.MethodType.UNARY);
    }

    /**
     * Creates a new client Streaming {@link ClientMethodDescriptor.Builder} with
     * the specified name.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the method name
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static ClientMethodDescriptor.Builder clientStreaming(String serviceName, String name) {
        return builder(serviceName, name, MethodDescriptor.MethodType.CLIENT_STREAMING);
    }

    /**
     * Creates a new server streaming {@link ClientMethodDescriptor.Builder} with
     * the specified name.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the method name
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static ClientMethodDescriptor.Builder serverStreaming(String serviceName, String name) {
        return builder(serviceName, name, MethodDescriptor.MethodType.SERVER_STREAMING);
    }

    /**
     * Creates a new bidirectional {@link ClientMethodDescriptor.Builder} with
     * the specified name.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the method name
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static ClientMethodDescriptor.Builder bidirectional(String serviceName, String name) {
        return builder(serviceName, name, MethodDescriptor.MethodType.BIDI_STREAMING);
    }

    /**
     * Return the {@link io.grpc.CallCredentials} set on this service.
     *
     * @return the {@link io.grpc.CallCredentials} set on this service
     */
    public CallCredentials callCredentials() {
        return this.callCredentials;
    }

    /**
     * Creates a new {@link ClientMethodDescriptor.Builder} with the specified name.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the method name
     * @param methodType  the gRPC method type
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static ClientMethodDescriptor.Builder builder(String serviceName,
                                                  String name,
                                                  MethodDescriptor.MethodType methodType) {

        MethodDescriptor.Builder builder = MethodDescriptor.newBuilder()
                .setFullMethodName(serviceName + "/" + name)
                .setType(methodType);

        return new Builder(serviceName, name, builder)
                .requestType(Object.class)
                .responseType(Object.class);
    }

    /**
     * Returns the simple name of the method.
     *
     * @return The simple name of the method.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the {@link io.grpc.MethodDescriptor} of this method.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     * @return The {@link io.grpc.MethodDescriptor} of this method.
     */
    @SuppressWarnings("unchecked")
    public <ReqT, RespT> MethodDescriptor<ReqT, RespT> descriptor() {
        return descriptor;
    }

    /**
     * Obtain the {@link ClientInterceptor}s to use for this method.
     *
     * @return the {@link ClientInterceptor}s to use for this method
     */
    PriorityBag<ClientInterceptor> interceptors() {
        return interceptors.readOnly();
    }

    /**
     * Obtain the {@link MethodHandler} to use to make client calls.
     *
     * @return the {@link MethodHandler} to use to make client calls
     */
    public MethodHandler methodHandler() {
        return methodHandler;
    }

    /**
     * ClientMethod configuration API.
     */
    public interface Rules {

        /**
         * Sets the type of parameter of this method.
         *
         * @param type The type of parameter of this method.
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules requestType(Class type);

        /**
         * Sets the type of parameter of this method.
         *
         * @param type The type of parameter of this method.
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules responseType(Class type);

        /**
         * Register one or more {@link ClientInterceptor interceptors} for the method.
         *
         * @param interceptors the interceptor(s) to register
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules intercept(ClientInterceptor... interceptors);

        /**
         * Register one or more {@link ClientInterceptor interceptors} for the method.
         * <p>
         * The added interceptors will be applied using the specified priority.
         *
         * @param priority     the priority to assign to the interceptors
         * @param interceptors one or more {@link ClientInterceptor}s to register
         * @return this {@link Rules} to allow fluent method chaining
         */
        Rules intercept(int priority, ClientInterceptor... interceptors);

        /**
         * Register the {@link MarshallerSupplier} for the method.
         * <p>
         * If not set the default {@link MarshallerSupplier} from the service will be used.
         *
         * @param marshallerSupplier the {@link MarshallerSupplier} for the service
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules marshallerSupplier(MarshallerSupplier marshallerSupplier);

        /**
         * Register the specified {@link io.grpc.CallCredentials} to be used for this method. This overrides
         * any {@link io.grpc.CallCredentials} set on the {@link io.helidon.grpc.client.ClientServiceDescriptor}.
         *
         * @param callCredentials the {@link io.grpc.CallCredentials} to set.
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules callCredentials(CallCredentials callCredentials);

        /**
         * Set the {@link MethodHandler} that can be used to invoke the method.
         *
         * @param methodHandler  the {2link MethodHandler} to use
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules methodHandler(MethodHandler methodHandler);
    }

    /**
     * {@link MethodDescriptor} builder implementation.
     */
    public static class Builder
            implements Rules, io.helidon.common.Builder<ClientMethodDescriptor> {

        private String name;
        private io.grpc.MethodDescriptor.Builder descriptor;
        private Class<?> requestType;
        private Class<?> responseType;
        private PriorityBag<ClientInterceptor> interceptors = PriorityBag.withDefaultPriority(InterceptorPriorities.USER);
        private MarshallerSupplier defaultMarshallerSupplier = MarshallerSupplier.defaultInstance();
        private MarshallerSupplier marshallerSupplier;
        private CallCredentials callCredentials;
        private MethodHandler methodHandler;

        /**
         * Constructs a new Builder instance.
         *
         * @param serviceName The name of the service ths method belongs to
         * @param name        the name of this method
         * @param descriptor  The gRPC method descriptor builder
         */
        Builder(String serviceName, String name, MethodDescriptor.Builder descriptor) {
            this.name = name;
            this.descriptor = descriptor.setFullMethodName(serviceName + "/" + name);
        }

        @Override
        public Builder requestType(Class type) {
            this.requestType = type;
            return this;
        }

        @Override
        public Builder responseType(Class type) {
            this.responseType = type;
            return this;
        }

        @Override
        public Builder intercept(ClientInterceptor... interceptors) {
            this.interceptors.addAll(Arrays.asList(interceptors));
            return this;
        }

        @Override
        public Builder intercept(int priority, ClientInterceptor... interceptors) {
            this.interceptors.addAll(Arrays.asList(interceptors), priority);
            return this;
        }

        @Override
        public Builder marshallerSupplier(MarshallerSupplier supplier) {
            this.marshallerSupplier = supplier;
            return this;
        }

        Builder defaultMarshallerSupplier(MarshallerSupplier supplier) {
            if (supplier == null) {
                this.defaultMarshallerSupplier = MarshallerSupplier.defaultInstance();
            } else {
                this.defaultMarshallerSupplier = supplier;
            }
            return this;
        }

        @Override
        public Builder methodHandler(MethodHandler methodHandler) {
            this.methodHandler = methodHandler;
            return this;
        }

        /**
         * Sets the full name of this Method.
         *
         * @param fullName the full name of the method
         * @return this builder instance for fluent API
         */
        Builder fullName(String fullName) {
            descriptor.setFullMethodName(fullName);
            this.name = fullName.substring(fullName.lastIndexOf('/') + 1);
            return this;
        }

        @Override
        public Rules callCredentials(CallCredentials callCredentials) {
            this.callCredentials = callCredentials;
            return this;
        }


        /**
         * Builds and returns a new instance of {@link ClientMethodDescriptor}.
         *
         * @return a new instance of {@link ClientMethodDescriptor}
         */
        @Override
        @SuppressWarnings("unchecked")
        public ClientMethodDescriptor build() {
            MarshallerSupplier supplier = this.marshallerSupplier;

            if (supplier == null) {
                supplier = defaultMarshallerSupplier;
            }

            if (requestType != null) {
                descriptor.setRequestMarshaller(supplier.get(requestType));
            }

            if (responseType != null) {
                descriptor.setResponseMarshaller(supplier.get(responseType));
            }

            return new ClientMethodDescriptor(name,
                                              descriptor.build(),
                                              interceptors,
                                              callCredentials,
                                              methodHandler);
        }

    }
}
