/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import java.lang.annotation.Annotation;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Builder;
import io.helidon.grpc.client.ClientMethodDescriptor;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.core.MethodHandler;
import io.helidon.microprofile.grpc.core.AbstractServiceBuilder;
import io.helidon.microprofile.grpc.core.AnnotatedMethod;
import io.helidon.microprofile.grpc.core.AnnotatedMethodList;
import io.helidon.microprofile.grpc.core.GrpcInterceptor;
import io.helidon.microprofile.grpc.core.GrpcInterceptorBinding;
import io.helidon.microprofile.grpc.core.GrpcInterceptors;
import io.helidon.microprofile.grpc.core.GrpcMarshaller;
import io.helidon.microprofile.grpc.core.GrpcMethod;
import io.helidon.microprofile.grpc.core.Instance;
import io.helidon.microprofile.grpc.core.ModelHelper;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.BeanManager;

import io.grpc.ClientInterceptor;

/**
 * A builder for constructing a {@link ClientServiceDescriptor.Builder} instances
 * from an annotated POJO.
 */
class GrpcClientBuilder
        extends AbstractServiceBuilder
        implements Builder<GrpcClientBuilder, ClientServiceDescriptor.Builder> {

    private static final System.Logger LOGGER = System.getLogger(GrpcClientBuilder.class.getName());

    private final BeanManager beanManager;

    /**
     * Create a {@link GrpcClientBuilder} for a given gRPC service class.
     *
     * @param serviceClass gRPC service (handler) class.
     * @param instance     the target instance to call gRPC handler methods on
     * @param beanManager  the {@link jakarta.enterprise.inject.spi.BeanManager} to use
     *                     to look-up CDI beans.
     * @throws NullPointerException if the service or instance parameters are null
     */
    private GrpcClientBuilder(Class<?> serviceClass, Supplier<?> instance, BeanManager beanManager) {
        super(serviceClass, instance);
        this.beanManager = beanManager;
    }

    /**
     * Create a {@link GrpcClientBuilder} for a given gRPC service.
     *
     * @param service the service to call gRPC handler methods on
     * @param beanManager  the {@link jakarta.enterprise.inject.spi.BeanManager} to use
     *                     to look-up CDI beans.
     * @throws NullPointerException if the service is null
     * @return a {@link GrpcClientBuilder}
     */
    static GrpcClientBuilder create(Object service, BeanManager beanManager) {
        return new GrpcClientBuilder(service.getClass(), Instance.singleton(service), beanManager);
    }

    /**
     * Create a {@link GrpcClientBuilder} for a given gRPC service class.
     *
     * @param serviceClass gRPC service (handler) class.
     * @param beanManager  the {@link jakarta.enterprise.inject.spi.BeanManager} to use
     *                     to look-up CDI beans.
     * @throws NullPointerException if the service class is null
     * @return a {@link GrpcClientBuilder}
     */
    static GrpcClientBuilder create(Class<?> serviceClass, BeanManager beanManager) {
        return new GrpcClientBuilder(Objects.requireNonNull(serviceClass), createInstanceSupplier(serviceClass), beanManager);
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

        addServiceMethods(builder, methodList, beanManager);
        configureClientInterceptors(builder, beanManager);

        LOGGER.log(Level.TRACE, () -> String.format("A new gRPC service was created by ServiceModeller: %s", builder));

        return builder;
    }

    /**
     * Add methods to the {@link ClientServiceDescriptor.Builder}.
     *
     * @param builder     the {@link ClientServiceDescriptor.Builder} to add the method to
     * @param methodList  the list of methods to add
     * @param beanManager  the {@link jakarta.enterprise.inject.spi.BeanManager} to use
     *                     to look-up CDI beans.
     */
    private void addServiceMethods(ClientServiceDescriptor.Builder builder, AnnotatedMethodList methodList, BeanManager beanManager) {
        for (AnnotatedMethod am : methodList.withAnnotation(GrpcMethod.class)) {
            addServiceMethod(builder, am, beanManager);
        }
        for (AnnotatedMethod am : methodList.withMetaAnnotation(GrpcMethod.class)) {
            addServiceMethod(builder, am, beanManager);
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
     * @param beanManager  the {@link jakarta.enterprise.inject.spi.BeanManager} to use
     *                     to look-up CDI beans.
     */
    private void addServiceMethod(ClientServiceDescriptor.Builder builder, AnnotatedMethod method, BeanManager beanManager) {
        GrpcMethod annotation = method.firstAnnotationOrMetaAnnotation(GrpcMethod.class);
        String name = determineMethodName(method, annotation);

        MethodHandler handler = handlerSuppliers().stream()
                .filter(supplier -> supplier.supplies(method))
                .findFirst()
                .map(supplier -> supplier.get(name, method, instanceSupplier()))
                .orElseThrow(() -> new IllegalArgumentException("Cannot locate a method handler supplier for method " + method));

        Class<?> requestType = handler.getRequestType();
        Class<?> responseType = handler.getResponseType();
        List<ClientInterceptor> interceptors = lookupMethodInterceptors(beanManager, method);

        GrpcInterceptors grpcInterceptors = method.getAnnotation(GrpcInterceptors.class);

        if (grpcInterceptors != null) {
            for (Class<?> interceptorClass : grpcInterceptors.value()) {
                ClientInterceptor interceptor = resolveInterceptor(beanManager, interceptorClass);
                if (interceptor != null) {
                    interceptors.add(interceptor);
                }
            }
        }

        AnnotatedMethodConfigurer configurer = new AnnotatedMethodConfigurer(method,
                                                                             requestType,
                                                                             responseType,
                                                                             interceptors,
                                                                             handler);

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
            LOGGER.log(Level.ERROR, () -> "Unrecognized method type " + annotation.type());
        }
    }

    private void configureClientInterceptors(ClientServiceDescriptor.Builder builder, BeanManager beanManager) {
        if (beanManager != null) {
            Class<?> serviceClass = serviceClass();
            Class<?> annotatedClass = annotatedServiceClass();

            configureClientInterceptors(builder, beanManager, serviceClass());

            if (!serviceClass.equals(annotatedClass)) {
                configureClientInterceptors(builder, beanManager, annotatedServiceClass());
            }
        }
    }

    private void configureClientInterceptors(ClientServiceDescriptor.Builder builder, BeanManager beanManager, Class<?> cls) {
        if (beanManager != null) {
            for (Annotation annotation : cls.getAnnotations()) {
                if (annotation.annotationType().isAnnotationPresent(GrpcInterceptorBinding.class)) {
                    builder.intercept(lookupInterceptor(annotation, beanManager));
                }
            }

            GrpcInterceptors grpcInterceptors = cls.getAnnotation(GrpcInterceptors.class);
            if (grpcInterceptors != null) {
                for (Class<?> interceptorClass : grpcInterceptors.value()) {
                    if (ClientInterceptor.class.isAssignableFrom(interceptorClass)) {
                        ClientInterceptor interceptor = resolveInterceptor(beanManager, interceptorClass);
                        if (interceptor != null) {
                            builder.intercept(interceptor);
                        }
                    }
                }
            }
        }
    }

    private List<ClientInterceptor> lookupMethodInterceptors(BeanManager beanManager, AnnotatedMethod method) {
        if (beanManager == null) {
            return Collections.emptyList();
        }

        List<ClientInterceptor> interceptors = new ArrayList<>();
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(GrpcInterceptorBinding.class)) {
                interceptors.add(lookupInterceptor(annotation, beanManager));
            }
        }
        return interceptors;
    }

    private ClientInterceptor lookupInterceptor(Annotation annotation, BeanManager beanManager) {
        jakarta.enterprise.inject.Instance<ClientInterceptor> instance;
        instance = beanManager.createInstance()
                .select(ClientInterceptor.class, GrpcInterceptor.Literal.INSTANCE);

        List<ClientInterceptor> interceptors = instance.stream()
                .filter(interceptor -> hasAnnotation(interceptor, annotation))
                .collect(Collectors.toList());

        if (interceptors.size() == 1) {
            return interceptors.get(0);
        } else if (interceptors.size() > 1) {
            throw new IllegalStateException("gRPC interceptor annotation"
                    + "resolves to ambiguous interceptor implementations "
                    + annotation);
        } else {
            throw new IllegalStateException("Cannot resolve a gRPC interceptor bean for annotation"
                    + annotation);
        }
    }

    private ClientInterceptor resolveInterceptor(BeanManager beanManager, Class<?> cls) {
        if (beanManager == null) {
            return null;
        }

        if (ClientInterceptor.class.isAssignableFrom(cls)) {
            jakarta.enterprise.inject.Instance<?> instance = beanManager.createInstance().select(cls, Any.Literal.INSTANCE);
            if (instance.isResolvable()) {
                return (ClientInterceptor) instance.get();
            } else {
                try {
                    return (ClientInterceptor) cls.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new IllegalArgumentException("Cannot create gRPC interceptor", e);
                }
            }
        } else {
            throw new IllegalArgumentException("Specified interceptor class " + cls + " is not a " + ClientInterceptor.class);
        }
    }

    private boolean hasAnnotation(ClientInterceptor interceptor, Annotation annotation) {
        Annotation[] annotations = getClass(interceptor).getAnnotations();
        return Arrays.asList(annotations).contains(annotation);
    }

    private Class<?> getClass(Object o) {
        Class<?> cls = o.getClass();
        while (cls.isSynthetic()) {
            cls = cls.getSuperclass();
        }
        return cls;
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
        private final List<ClientInterceptor> interceptors;
        private final MethodHandler methodHandler;

        private AnnotatedMethodConfigurer(AnnotatedMethod method,
                                          Class<?> requestType,
                                          Class<?> responseType,
                                          List<ClientInterceptor> interceptors,
                                          MethodHandler methodHandler) {
            this.method = method;
            this.requestType = requestType;
            this.responseType = responseType;
            this.methodHandler = methodHandler;
            this.interceptors = interceptors;
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
