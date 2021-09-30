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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.inject.Singleton;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.grpc.core.MarshallerSupplier;

/**
 * A base class for gRPC service and client descriptor builders.
 */
public abstract class AbstractServiceBuilder {

    private static final Logger LOGGER = Logger.getLogger(AbstractServiceBuilder.class.getName());

    private final Class<?> serviceClass;
    private final Class<?> annotatedServiceClass;
    private final Supplier<?> instance;
    private final List<MethodHandlerSupplier> handlerSuppliers;

    /**
     * Create a new introspection modeller for a given gRPC service class.
     *
     * @param serviceClass gRPC service (handler) class.
     * @param instance     the target instance to call gRPC handler methods on
     * @throws NullPointerException if the service or instance parameters are null
     */
    protected AbstractServiceBuilder(Class<?> serviceClass, Supplier<?> instance) {
        this.serviceClass = Objects.requireNonNull(serviceClass);
        this.annotatedServiceClass = ModelHelper.getAnnotatedResourceClass(serviceClass, Grpc.class);
        this.instance = Objects.requireNonNull(instance);
        this.handlerSuppliers = loadHandlerSuppliers();
    }

    /**
     * Determine whether this modeller contains an annotated service.
     *
     * @return  {@code true} if this modeller contains an annotated service
     */
    public boolean isAnnotatedService() {
        return annotatedServiceClass.isAnnotationPresent(Grpc.class);
    }

    /**
     * Obtain the service class.
     * @return  the service class
     */
    protected Class<?> serviceClass() {
        return serviceClass;
    }

    /**
     * Obtain the actual annotated class.
     * @return  the actual annotated class
     */
    protected Class<?> annotatedServiceClass() {
        return annotatedServiceClass;
    }

    /**
     * Obtain the {@link MarshallerSupplier} to use.
     * <p>
     * The {@link MarshallerSupplier} will be determined by the {@link GrpcMarshaller}
     * annotation if it is present otherwise the default supplier will be returned.
     *
     * @return  the {@link MarshallerSupplier} to use
     */
    protected MarshallerSupplier getMarshallerSupplier() {
        GrpcMarshaller annotation = annotatedServiceClass.getAnnotation(GrpcMarshaller.class);
        return annotation == null ? MarshallerSupplier.defaultInstance() : ModelHelper.getMarshallerSupplier(annotation);
    }

    /**
     * Create the service instance supplier.
     *
     * @param cls  the service class
     * @return the service instance supplier
     */
    protected static Supplier<?> createInstanceSupplier(Class<?> cls) {
        if (cls.isAnnotationPresent(Singleton.class)) {
            return Instance.singleton(cls);
        } else {
            return Instance.create(cls);
        }
    }

    /**
     * Verify that there are no non-public annotated methods.
     */
    protected void checkForNonPublicMethodIssues() {
        AnnotatedMethodList allDeclaredMethods = AnnotatedMethodList.create(getAllDeclaredMethods(serviceClass));

        // log warnings for all non-public annotated methods
        allDeclaredMethods.withMetaAnnotation(GrpcMethod.class).isNotPublic()
                .forEach(method -> LOGGER.log(Level.WARNING, () -> String.format("The gRPC method, %s, MUST be "
                                              + "public scoped otherwise the method is ignored", method)));
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
        return instance;
    }

    /**
     * Obtain a list of all of the methods declared on the service class.
     *
     * @param clazz  the service class
     * @return a list of all of the methods declared on the service class
     */
    protected List<Method> getAllDeclaredMethods(Class<?> clazz) {
        List<Method> result = new LinkedList<>();

        AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
            Class current = clazz;
            while (current != Object.class && current != null) {
                result.addAll(Arrays.asList(current.getDeclaredMethods()));
                current = current.getSuperclass();
            }
            return null;
        });

        return result;
    }

    /**
     * Determine the name of the gRPC service.
     * <p>
     * If the class is annotated with {@link Grpc}
     * then the name value from the annotation is used as the service name. If the annotation
     * has no name value or the annotation is not present the simple name of the class is used.
     *
     * @param annotatedClass  the annotated class
     * @return the name of the gRPC service
     */
    protected String determineServiceName(Class<?> annotatedClass) {
        Grpc serviceAnnotation = annotatedClass.getAnnotation(Grpc.class);
        String name = null;

        if (serviceAnnotation != null) {
            name = serviceAnnotation.name().trim();
        }

        if (name == null || name.trim().isEmpty()) {
            name = annotatedClass.getSimpleName();
        }

        return name;
    }

    /**
     * Determine the name to use from the method.
     * <p>
     * If the method is annotated with {@link GrpcMethod} then use the value of {@link GrpcMethod#name()}
     * unless {@link GrpcMethod#name()} returns empty string, in which case use the actual method name.
     * <p>
     * If the method is annotated with an annotation that has the meta-annotation {@link GrpcMethod} then use
     * the value of that annotation's {@code name()} method. If that annotation does not have a {@code name()}
     * method or the {@code name()} method return empty string then use the actual method name.
     *
     * @param method      the annotated method
     * @param annotation  the method type annotation
     * @return the value to use for the method name
     */
    public static String determineMethodName(AnnotatedMethod method, GrpcMethod annotation) {
        Annotation actualAnnotation = method.annotationsWithMetaAnnotation(GrpcMethod.class)
                .findFirst()
                .orElse(annotation);

        String name = null;
        try {
            Method m = actualAnnotation.annotationType().getMethod("name");
            name = (String) m.invoke(actualAnnotation);
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.WARNING, () -> String.format("Annotation %s has no name() method", actualAnnotation));
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.log(Level.WARNING, e, () -> String.format("Error calling name() method on annotation %s", actualAnnotation));
        }

        if (name == null || name.trim().isEmpty()) {
            name = method.method().getName();
        }

        return name;
    }

    /**
     * Load the {@link io.helidon.microprofile.grpc.core.MethodHandlerSupplier} instances using the {@link java.util.ServiceLoader}
     * and return them in priority order.
     * <p>
     * Priority is determined by the value obtained from the {@link javax.annotation.Priority} annotation on
     * any implementation classes. Classes not annotated with {@link javax.annotation.Priority} have a
     * priority of zero.
     *
     * @return a priority ordered list of {@link io.helidon.microprofile.grpc.core.MethodHandlerSupplier} instances
     */
    private List<MethodHandlerSupplier> loadHandlerSuppliers() {
        List<MethodHandlerSupplier> list = new ArrayList<>();

        HelidonServiceLoader.create(ServiceLoader.load(MethodHandlerSupplier.class)).forEach(list::add);

        list.sort((left, right) -> {
            Priority leftPriority = left.getClass().getAnnotation(Priority.class);
            Priority rightPriority = right.getClass().getAnnotation(Priority.class);
            int leftValue = leftPriority == null ? 0 : leftPriority.value();
            int rightValue = rightPriority == null ? 0 : rightPriority.value();
            return leftValue - rightValue;
        });

        return list;
    }
}
