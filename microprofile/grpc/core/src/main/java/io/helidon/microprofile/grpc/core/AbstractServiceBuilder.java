/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.grpc.api.Grpc;
import io.helidon.grpc.core.MarshallerSupplier;

import jakarta.inject.Singleton;

import static java.lang.System.Logger.Level;

/**
 * A base class for gRPC service and client descriptor builders.
 */
public abstract class AbstractServiceBuilder {

    private static final System.Logger LOGGER = System.getLogger(AbstractServiceBuilder.class.getName());

    private final Class<?> serviceClass;
    private final Class<?> annotatedServiceClass;
    private final Supplier<?> instanceSupplier;
    private final List<MethodHandlerSupplier> handlerSuppliers;

    /**
     * Create a new introspection modeller for a given gRPC service class.
     *
     * @param serviceClass gRPC service (handler) class.
     * @param instanceSupplier     the target instanceSupplier to call gRPC handler methods on
     * @throws NullPointerException if the service or instanceSupplier parameters are null
     */
    protected AbstractServiceBuilder(Class<?> serviceClass, Supplier<?> instanceSupplier) {
        this.serviceClass = Objects.requireNonNull(serviceClass);
        this.annotatedServiceClass = ModelHelper.getAnnotatedResourceClass(serviceClass, Grpc.GrpcService.class);
        this.instanceSupplier = Objects.requireNonNull(instanceSupplier);
        this.handlerSuppliers = HelidonServiceLoader.create(ServiceLoader.load(MethodHandlerSupplier.class)).asList();
    }

    /**
     * Determine whether this modeller contains an annotated service.
     *
     * @return  {@code true} if this modeller contains an annotated service
     */
    public boolean isAnnotatedService() {
        return annotatedServiceClass.isAnnotationPresent(Grpc.GrpcService.class);
    }

    /**
     * Determine the name to use from the method.
     * <p>
     * If the method is annotated with {@link io.helidon.grpc.api.Grpc.GrpcMethod} or an annotation that is annotated with
     * {@link io.helidon.grpc.api.Grpc.GrpcMethod}, then attempt to determine the method name from the annotation. If unable,
     * use the actual method name.
     *
     * @param method      the annotated method
     * @param annotation  the method type annotation
     * @return the value to use for the method name
     */
    public static String determineMethodName(AnnotatedMethod method, Grpc.GrpcMethod annotation) {
        Annotation actualAnnotation = method.annotationsWithMetaAnnotation(Grpc.GrpcMethod.class)
                .findFirst()
                .orElse(annotation);

        String name = nameFromMember(actualAnnotation, "value");    // @GrpcMethod is meta annotation
        if (name == null || name.trim().isEmpty()) {
            name = nameFromMember(actualAnnotation, "name");        // @GrpcMethod is actual annotation
        }
        if (name == null || name.trim().isEmpty()) {
            name = method.method().getName();
        }
        return name;
    }

    /**
     * Obtain the service class.
     * @return the service class
     */
    protected Class<?> serviceClass() {
        return serviceClass;
    }

    /**
     * Obtain the actual annotated class.
     * @return the actual annotated class
     */
    protected Class<?> annotatedServiceClass() {
        return annotatedServiceClass;
    }

    /**
     * Obtain the {@link MarshallerSupplier} to use.
     * <p>
     * The {@link MarshallerSupplier} will be determined by the {@link io.helidon.grpc.api.Grpc.GrpcMarshaller}
     * annotation if it is present otherwise the default supplier will be returned.
     *
     * @return the {@link MarshallerSupplier} to use
     */
    protected MarshallerSupplier getMarshallerSupplier() {
        Grpc.GrpcMarshaller annotation = annotatedServiceClass.getAnnotation(Grpc.GrpcMarshaller.class);
        return annotation == null ? MarshallerSupplier.create() : ModelHelper.getMarshallerSupplier(annotation);
    }

    /**
     * Create the service instance supplier.
     *
     * @param cls  the service class
     * @return the service instance supplier
     */
    protected static Supplier<?> createInstanceSupplier(Class<?> cls) {
        if (cls.isAnnotationPresent(Singleton.class)) {
            return InstanceSupplier.singleton(cls);
        } else {
            return InstanceSupplier.create(cls);
        }
    }

    /**
     * Verify that there are no non-public annotated methods.
     */
    protected void checkForNonPublicMethodIssues() {
        AnnotatedMethodList allDeclaredMethods = AnnotatedMethodList.create(getAllDeclaredMethods(serviceClass));

        // log warnings for all non-public annotated methods
        allDeclaredMethods.withMetaAnnotation(Grpc.GrpcMethod.class).isNotPublic()
                .forEach(method -> LOGGER.log(Level.WARNING, () -> String.format("The gRPC method, %s, MUST be "
                                                                                         + "public scoped otherwise the method "
                                                                                         + "is ignored",
                                                                                 method)));
    }

    /**
     * Obtain the list of method handler suppliers.
     *
     * @return the list of method handler suppliers
     */
    protected List<MethodHandlerSupplier> handlerSuppliers() {
        return handlerSuppliers;
    }

    /**
     * Obtain the service instance supplier.
     *
     * @return the service instance supplier
     */
    protected Supplier<?> instanceSupplier() {
        return instanceSupplier;
    }

    /**
     * Obtain a list of all of the methods declared on the service class.
     *
     * @param clazz  the service class
     * @return a list of all of the methods declared on the service class
     */
    protected List<Method> getAllDeclaredMethods(Class<?> clazz) {
        List<Method> result = new LinkedList<>();
        Class<?> current = clazz;
        while (current != Object.class && current != null) {
            result.addAll(Arrays.asList(current.getDeclaredMethods()));
            current = current.getSuperclass();
        }
        return result;
    }

    /**
     * Determine the name of the gRPC service.
     * <p>
     * If the class is annotated with {@link io.helidon.grpc.api.Grpc.GrpcService}
     * then the name value from the annotation is used as the service name. If the annotation
     * has no name value or the annotation is not present the simple name of the class is used.
     *
     * @param annotatedClass  the annotated class
     * @return the name of the gRPC service
     */
    protected String determineServiceName(Class<?> annotatedClass) {
        Grpc.GrpcService serviceAnnotation = annotatedClass.getAnnotation(Grpc.GrpcService.class);
        String name = null;

        if (serviceAnnotation != null) {
            name = serviceAnnotation.value().trim();
        }

        if (name == null || name.trim().isEmpty()) {
            name = annotatedClass.getSimpleName();
        }

        return name;
    }

    /**
     * Get method from annotation member if present and of type {@link String}.
     *
     * @param annotation the annotation
     * @param member the annotation method to call
     * @return method name or {@code null}
     */
    private static String nameFromMember(Annotation annotation, String member) {
        try {
            Method m = annotation.annotationType().getMethod(member);
            Object value = m.invoke(annotation);
            return value instanceof String s ? s : null;
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.WARNING, () -> String.format("Annotation %s has no name() method", annotation));
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.log(Level.WARNING, () -> String.format("Error calling name() method on annotation %s", annotation), e);
        }
        return null;
    }
}
