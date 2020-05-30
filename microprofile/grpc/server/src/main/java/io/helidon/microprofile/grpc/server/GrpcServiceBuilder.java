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

package io.helidon.microprofile.grpc.server;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.BeanManager;

import io.helidon.common.Builder;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.grpc.core.ContextKeys;
import io.helidon.grpc.core.MethodHandler;
import io.helidon.grpc.server.MethodDescriptor;
import io.helidon.grpc.server.ServiceDescriptor;
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

import io.grpc.ServerInterceptor;

/**
 * A builder for constructing a {@link ServiceDescriptor}
 * instances from an annotated POJOs.
 */
public class GrpcServiceBuilder
        extends AbstractServiceBuilder
        implements Builder<ServiceDescriptor> {

    private static final Logger LOGGER = Logger.getLogger(GrpcServiceBuilder.class.getName());

    private final BeanManager beanManager;

    /**
     * Create a new introspection modeller for a given gRPC service class.
     *
     * @param serviceClass gRPC service (handler) class.
     * @param instance     the target instance to call gRPC handler methods on
     * @param beanManager  the {@link javax.enterprise.inject.spi.BeanManager} to use
     *                     to look-up CDI beans.
     * @throws java.lang.NullPointerException if the service or instance parameters are null
     */
    private GrpcServiceBuilder(Class<?> serviceClass, Supplier<?> instance, BeanManager beanManager) {
        super(serviceClass, instance);
        this.beanManager = beanManager;
    }

    /**
     * Create a new introspection modeller for a given gRPC service.
     *
     * @param service      the service to call gRPC handler methods on
     * @param beanManager  the {@link javax.enterprise.inject.spi.BeanManager} to use
     *                     to look-up CDI beans.
     * @throws java.lang.NullPointerException if the service is null
     * @return a {@link GrpcServiceBuilder}
     */
    public static GrpcServiceBuilder create(Object service, BeanManager beanManager) {
        return new GrpcServiceBuilder(service.getClass(), Instance.singleton(service), beanManager);
    }

    /**
     * Create a new introspection modeller for a given gRPC service class.
     *
     * @param serviceClass gRPC service (handler) class.
     * @param beanManager  the {@link javax.enterprise.inject.spi.BeanManager} to use
     *                     to look-up CDI beans.
     * @throws java.lang.NullPointerException if the service class is null
     * @return a {@link GrpcServiceBuilder}
     */
    public static GrpcServiceBuilder create(Class<?> serviceClass, BeanManager beanManager) {
        return new GrpcServiceBuilder(Objects.requireNonNull(serviceClass), createInstanceSupplier(serviceClass), beanManager);
    }

    /**
     * Create a {@link GrpcServiceBuilder} for a given gRPC service class.
     *
     * @param serviceClass gRPC service (handler) class.
     * @param instance     the target instance to call gRPC handler methods on
     * @param beanManager  the {@link javax.enterprise.inject.spi.BeanManager} to use
     *                     to look-up CDI beans.
     * @throws java.lang.NullPointerException if the service or instance parameters are null
     * @return a {@link GrpcServiceBuilder}
     */
    public static GrpcServiceBuilder create(Class<?> serviceClass, Supplier<?> instance, BeanManager beanManager) {
        return new GrpcServiceBuilder(serviceClass, instance, beanManager);
    }

    /**
     * Create a {@link ServiceDescriptor.Builder} introspected class.
     *
     * @return a {@link ServiceDescriptor.Builder} for the introspected class.
     */
    @Override
    public ServiceDescriptor build() {
        checkForNonPublicMethodIssues();

        Class<?> annotatedServiceClass = annotatedServiceClass();
        AnnotatedMethodList methodList = AnnotatedMethodList.create(annotatedServiceClass);
        String name = determineServiceName(annotatedServiceClass);

        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(serviceClass(), name)
                .marshallerSupplier(getMarshallerSupplier());

        addServiceMethods(builder, methodList, beanManager);
        configureServiceInterceptors(builder, beanManager);

        Class<?> serviceClass = serviceClass();
        Class<?> annotatedClass = annotatedServiceClass();
        HelidonServiceLoader.create(ServiceLoader.load(AnnotatedServiceConfigurer.class))
                .forEach(configurer -> configurer.accept(serviceClass, annotatedClass, builder));

        return builder.build();
    }

    /**
     * Add methods to the {@link ServiceDescriptor.Builder}.
     *
     * @param builder      the {@link ServiceDescriptor.Builder} to add the method to
     * @param methodList   the list of methods to add
     * @param beanManager  the {@link javax.enterprise.inject.spi.BeanManager} to use
     *                     to look-up CDI beans.
     */
    private void addServiceMethods(ServiceDescriptor.Builder builder, AnnotatedMethodList methodList, BeanManager beanManager) {
        for (AnnotatedMethod am : methodList.withAnnotation(GrpcMethod.class)) {
            addServiceMethod(builder, am, beanManager);
        }
        for (AnnotatedMethod am : methodList.withMetaAnnotation(GrpcMethod.class)) {
            addServiceMethod(builder, am, beanManager);
        }
    }

    /**
     * Add a method to the {@link ServiceDescriptor.Builder}.
     * <p>
     * The method configuration will be determined by the annotations present on the
     * method and the method signature.
     *
     * @param builder  the {@link ServiceDescriptor.Builder} to add the method to
     * @param method   the {@link AnnotatedMethod} representing the method to add
     * @param beanManager  the {@link javax.enterprise.inject.spi.BeanManager} to use
     *                     to look-up CDI beans.
     */
    @SuppressWarnings("unchecked")
    private void addServiceMethod(ServiceDescriptor.Builder builder, AnnotatedMethod method, BeanManager beanManager) {
        GrpcMethod annotation = method.firstAnnotationOrMetaAnnotation(GrpcMethod.class);
        String name = determineMethodName(method, annotation);
        Supplier<?> instanceSupplier = instanceSupplier();

        MethodHandler handler = handlerSuppliers().stream()
                .filter(supplier -> supplier.supplies(method))
                .findFirst()
                .map(supplier -> supplier.get(name, method, instanceSupplier))
                .orElseThrow(() -> new IllegalArgumentException("Cannot locate a method handler supplier for method " + method));

        Class<?> requestType = handler.getRequestType();
        Class<?> responseType = handler.getResponseType();
        List<ServerInterceptor> interceptors = lookupMethodInterceptors(beanManager, method);

        GrpcInterceptors grpcInterceptors = method.getAnnotation(GrpcInterceptors.class);

        if (grpcInterceptors != null) {
            for (Class<?> interceptorClass : grpcInterceptors.value()) {
                ServerInterceptor interceptor = resolveInterceptor(beanManager, interceptorClass);
                if (interceptor != null) {
                    interceptors.add(interceptor);
                }
            }
        }

        AnnotatedMethodConfigurer configurer = new AnnotatedMethodConfigurer(method,
                                                                             requestType,
                                                                             responseType,
                                                                             interceptors);

        switch (annotation.type()) {
        case UNARY:
            builder.unary(name, handler, configurer);
            break;
        case CLIENT_STREAMING:
            builder.clientStreaming(name, handler, configurer);
            break;
        case SERVER_STREAMING:
            builder.serverStreaming(name, handler, configurer);
            break;
        case BIDI_STREAMING:
            builder.bidirectional(name, handler, configurer);
            break;
        case UNKNOWN:
        default:
            LOGGER.log(Level.SEVERE, () -> "Unrecognized method type " + annotation.type());
        }
    }

    private void configureServiceInterceptors(ServiceDescriptor.Builder builder, BeanManager beanManager) {
        if (beanManager != null) {
            Class<?> serviceClass = serviceClass();
            Class<?> annotatedClass = annotatedServiceClass();

            configureServiceInterceptors(builder, beanManager, serviceClass());

            if (!serviceClass.equals(annotatedClass)) {
                configureServiceInterceptors(builder, beanManager, annotatedServiceClass());
            }
        }
    }

    private void configureServiceInterceptors(ServiceDescriptor.Builder builder, BeanManager beanManager, Class<?> cls) {
        if (beanManager != null) {
            for (Annotation annotation : cls.getAnnotations()) {
                if (annotation.annotationType().isAnnotationPresent(GrpcInterceptorBinding.class)) {
                    builder.intercept(lookupInterceptor(annotation, beanManager));
                }
            }

            GrpcInterceptors grpcInterceptors = cls.getAnnotation(GrpcInterceptors.class);
            if (grpcInterceptors != null) {
                for (Class<?> interceptorClass : grpcInterceptors.value()) {
                    if (ServerInterceptor.class.isAssignableFrom(interceptorClass)) {
                        ServerInterceptor interceptor = resolveInterceptor(beanManager, interceptorClass);
                        if (interceptor != null) {
                            builder.intercept(interceptor);
                        }
                    }
                }
            }
        }
    }

    private List<ServerInterceptor> lookupMethodInterceptors(BeanManager beanManager, AnnotatedMethod method) {
        if (beanManager == null) {
            return Collections.emptyList();
        }

        List<ServerInterceptor> interceptors = new ArrayList<>();
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(GrpcInterceptorBinding.class)) {
                interceptors.add(lookupInterceptor(annotation, beanManager));
            }
        }
        return interceptors;
    }

    private ServerInterceptor lookupInterceptor(Annotation annotation, BeanManager beanManager) {
        javax.enterprise.inject.Instance<ServerInterceptor> instance;
        instance = beanManager.createInstance()
                .select(ServerInterceptor.class, GrpcInterceptor.Literal.INSTANCE);

        List<ServerInterceptor> interceptors = instance.stream()
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

    private ServerInterceptor resolveInterceptor(BeanManager beanManager, Class<?> cls) {
        if (beanManager == null) {
            return null;
        }

        if (ServerInterceptor.class.isAssignableFrom(cls)) {
            javax.enterprise.inject.Instance<?> instance = beanManager.createInstance().select(cls, Any.Literal.INSTANCE);
            if (instance.isResolvable()) {
                return (ServerInterceptor) instance.get();
            } else {
                try {
                    return (ServerInterceptor) cls.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new IllegalArgumentException("Cannot create gRPC interceptor", e);
                }
            }
        } else {
            throw new IllegalArgumentException("Specified interceptor class " + cls + " is not a " + ServerInterceptor.class);
        }
    }

    private boolean hasAnnotation(ServerInterceptor interceptor, Annotation annotation) {
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
     * A {@link Consumer} of {@link MethodDescriptor.Rules} that
     * applies configuration changes based on annotations present
     * on the gRPC method.
     */
    private static class AnnotatedMethodConfigurer
            implements MethodDescriptor.Configurer {

        private final AnnotatedMethod method;
        private final Class<?> requestType;
        private final Class<?> responseType;
        private final List<ServerInterceptor> interceptors;

        private AnnotatedMethodConfigurer(AnnotatedMethod method,
                                          Class<?> requestType,
                                          Class<?> responseType,
                                          List<ServerInterceptor> interceptors) {
            this.method = method;
            this.requestType = requestType;
            this.responseType = responseType;
            this.interceptors = interceptors;
        }

        @Override
        public void configure(MethodDescriptor.Rules rules) {
            rules.addContextValue(ContextKeys.SERVICE_METHOD, method.declaredMethod())
                  .requestType(requestType)
                  .responseType(responseType);

            if (method.isAnnotationPresent(GrpcMarshaller.class)) {
                rules.marshallerSupplier(ModelHelper.getMarshallerSupplier(method.getAnnotation(GrpcMarshaller.class)));
            }

            interceptors.forEach(rules::intercept);
        }
    }
}
