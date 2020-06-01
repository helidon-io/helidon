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

package io.helidon.microprofile.grpc.client;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.Builder;
import io.helidon.grpc.client.ClientMethodDescriptor;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.core.MethodHandler;
import io.helidon.microprofile.grpc.core.AbstractServiceBuilder;
import io.helidon.microprofile.grpc.core.AnnotatedMethod;
import io.helidon.microprofile.grpc.core.AnnotatedMethodList;
import io.helidon.microprofile.grpc.core.GrpcMarshaller;
import io.helidon.microprofile.grpc.core.GrpcMethod;
import io.helidon.microprofile.grpc.core.Instance;
import io.helidon.microprofile.grpc.core.ModelHelper;

/**
 * A builder for constructing a {@link ClientServiceDescriptor.Builder} instances
 * from an annotated POJO.
 */
class GrpcClientBuilder
        extends AbstractServiceBuilder
        implements Builder<ClientServiceDescriptor.Builder> {

    private static final Logger LOGGER = Logger.getLogger(GrpcClientBuilder.class.getName());

    /**
     * Create a {@link GrpcClientBuilder} for a given gRPC service class.
     *
     * @param serviceClass gRPC service (handler) class.
     * @param instance     the target instance to call gRPC handler methods on
     * @throws NullPointerException if the service or instance parameters are null
     */
    private GrpcClientBuilder(Class<?> serviceClass, Supplier<?> instance) {
        super(serviceClass, instance);
    }

    /**
     * Create a {@link GrpcClientBuilder} for a given gRPC service.
     *
     * @param service the service to call gRPC handler methods on
     * @throws NullPointerException if the service is null
     * @return a {@link GrpcClientBuilder}
     */
    static GrpcClientBuilder create(Object service) {
        return new GrpcClientBuilder(service.getClass(), Instance.singleton(service));
    }

    /**
     * Create a {@link GrpcClientBuilder} for a given gRPC service class.
     *
     * @param serviceClass gRPC service (handler) class.
     * @throws NullPointerException if the service class is null
     * @return a {@link GrpcClientBuilder}
     */
    static GrpcClientBuilder create(Class<?> serviceClass) {
        return new GrpcClientBuilder(Objects.requireNonNull(serviceClass), createInstanceSupplier(serviceClass));
    }

    /**
     * Create a new resource model builder for the introspected class.
     * <p>
     * The model returned is filled with the introspected data.
     * </p>
     *
     * @return new resource model builder for the introspected class.
     */
    @Override
    public ClientServiceDescriptor.Builder build() {
        checkForNonPublicMethodIssues();

        Class<?> annotatedServiceClass = annotatedServiceClass();
        AnnotatedMethodList methodList = AnnotatedMethodList.create(annotatedServiceClass);
        String name = determineServiceName(annotatedServiceClass);

        ClientServiceDescriptor.Builder builder = ClientServiceDescriptor.builder(serviceClass())
                .name(name)
                .marshallerSupplier(getMarshallerSupplier());

        addServiceMethods(builder, methodList);

        LOGGER.log(Level.FINEST, () -> String.format("A new gRPC service was created by ServiceModeller: %s", builder));

        return builder;
    }

    /**
     * Add methods to the {@link ClientServiceDescriptor.Builder}.
     *
     * @param builder     the {@link ClientServiceDescriptor.Builder} to add the method to
     * @param methodList  the list of methods to add
     */
    private void addServiceMethods(ClientServiceDescriptor.Builder builder, AnnotatedMethodList methodList) {
        for (AnnotatedMethod am : methodList.withAnnotation(GrpcMethod.class)) {
            addServiceMethod(builder, am);
        }
        for (AnnotatedMethod am : methodList.withMetaAnnotation(GrpcMethod.class)) {
            addServiceMethod(builder, am);
        }
    }

    /**
     * Add a method to the {@link ClientServiceDescriptor.Builder}.
     * <p>
     * The method configuration will be determined by the annotations present on the
     * method and the method signature.
     *
     * @param builder  the {@link ClientServiceDescriptor.Builder} to add the method to
     * @param method   the {@link io.helidon.microprofile.grpc.core.AnnotatedMethod} representing the method to add
     */
    private void addServiceMethod(ClientServiceDescriptor.Builder builder, AnnotatedMethod method) {
        GrpcMethod annotation = method.firstAnnotationOrMetaAnnotation(GrpcMethod.class);
        String name = determineMethodName(method, annotation);

        MethodHandler handler = handlerSuppliers().stream()
                .filter(supplier -> supplier.supplies(method))
                .findFirst()
                .map(supplier -> supplier.get(name, method, instanceSupplier()))
                .orElseThrow(() -> new IllegalArgumentException("Cannot locate a method handler supplier for method " + method));

        Class<?> requestType = handler.getRequestType();
        Class<?> responseType = handler.getResponseType();
        AnnotatedMethodConfigurer configurer = new AnnotatedMethodConfigurer(method, requestType, responseType, handler);

        switch (annotation.type()) {
        case UNARY:
            builder.unary(name, configurer);
            break;
        case CLIENT_STREAMING:
            builder.clientStreaming(name, configurer);
            break;
        case SERVER_STREAMING:
            builder.serverStreaming(name, configurer);
            break;
        case BIDI_STREAMING:
            builder.bidirectional(name, configurer);
            break;
        case UNKNOWN:
        default:
            LOGGER.log(Level.SEVERE, () -> "Unrecognized method type " + annotation.type());
        }
    }

    /**
     * A {@link java.util.function.Consumer} of {@link ClientMethodDescriptor.Rules}
     * that applies configuration changes based on annotations present on the gRPC
     * method.
     */
    private static class AnnotatedMethodConfigurer
            implements Consumer<ClientMethodDescriptor.Rules> {

        private final AnnotatedMethod method;
        private final Class<?> requestType;
        private final Class<?> responseType;
        private final MethodHandler methodHandler;

        private AnnotatedMethodConfigurer(AnnotatedMethod method,
                                          Class<?> requestType,
                                          Class<?> responseType,
                                          MethodHandler methodHandler) {
            this.method = method;
            this.requestType = requestType;
            this.responseType = responseType;
            this.methodHandler = methodHandler;
        }

        @Override
        public void accept(ClientMethodDescriptor.Rules config) {
            config.requestType(requestType)
                  .responseType(responseType)
                  .methodHandler(methodHandler);

            if (method.isAnnotationPresent(GrpcMarshaller.class)) {
                config.marshallerSupplier(ModelHelper.getMarshallerSupplier(method.getAnnotation(GrpcMarshaller.class)));
            }
        }
    }
}
