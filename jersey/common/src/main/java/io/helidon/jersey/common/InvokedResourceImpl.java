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
package io.helidon.jersey.common;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;

import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.ResourceMethod;

final class InvokedResourceImpl implements InvokedResource {
    private static final ConcurrentHashMap<MethodAnnotationKey, Optional<? extends Annotation>> METHOD_ANNOTATION_CACHE =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ClassAnnotationKey, Optional<? extends Annotation>> CLASS_ANNOTATION_CACHE =
            new ConcurrentHashMap<>();

    private final Optional<Class<?>> definitionClass;
    private final Optional<Class<?>> handlingClass;
    private final Optional<Method> definitionMethod;
    private final Optional<Method> handlingMethod;

    private InvokedResourceImpl(Optional<Method> definitionMethod,
                                Optional<Method> handlingMethod,
                                Optional<Class<?>> handlingClass,
                                Optional<Class<?>> definitionClass) {

        this.definitionMethod = definitionMethod;
        this.handlingMethod = handlingMethod;
        this.handlingClass = handlingClass;
        this.definitionClass = definitionClass;
    }

    static InvokedResource create(ContainerRequestContext context) {
        ExtendedUriInfo uriInfo = (ExtendedUriInfo) context.getUriInfo();
        ResourceMethod matchedResourceMethod = uriInfo.getMatchedResourceMethod();
        Invocable invocable = matchedResourceMethod.getInvocable();

        Class<?> handlingClass = invocable.getHandler().getHandlerClass();
        Method handlingMethod = invocable.getHandlingMethod();

        Class<?> definitionClass = getDefinitionClass(handlingClass);
        Method definitionMethod = invocable.getDefinitionMethod();

        return new InvokedResourceImpl(
                Optional.ofNullable(definitionMethod),
                Optional.ofNullable(handlingMethod),
                Optional.ofNullable(handlingClass),
                Optional.ofNullable(definitionClass)
        );
    }

    @Override
    public Optional<Method> definitionMethod() {
        return definitionMethod;
    }

    @Override
    public Optional<Method> handlingMethod() {
        return handlingMethod;
    }

    @Override
    public Optional<Class<?>> definitionClass() {
        return definitionClass;
    }

    @Override
    public Optional<Class<?>> handlingClass() {
        return handlingClass;
    }

    @Override
    public <T extends Annotation> Optional<T> findAnnotation(Class<T> annotationClass) {
        return findMethodAnnotation(annotationClass)
                .or(() -> findClassAnnotation(annotationClass));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Annotation> Optional<T> findMethodAnnotation(Class<T> annotationClass) {
        if (!handlingMethod.isPresent() || !handlingClass.isPresent()) {
            return Optional.empty();
        }

        Method theMethod = handlingMethod().get();
        Class<?> theClass = handlingClass.get();
        Class<?> definitionClass = definitionClass().orElse(theClass);

        return (Optional<T>) METHOD_ANNOTATION_CACHE
                .computeIfAbsent(new MethodAnnotationKey(annotationClass, definitionClass, theClass, theMethod),
                                 aKey -> findMethodAnnotation(annotationClass,
                                                              theClass,
                                                              theMethod));

    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Annotation> Optional<T> findClassAnnotation(Class<T> annotationClass) {
        if (!handlingClass.isPresent()) {
            return Optional.empty();
        }

        Class<?> theClass = handlingClass.get();

        return (Optional<T>) CLASS_ANNOTATION_CACHE.computeIfAbsent(new ClassAnnotationKey(annotationClass, theClass),
                                                                    aKey -> findClassAnnotation(annotationClass,
                                                                                                theClass));
    }

    private <T extends Annotation> Optional<? extends Annotation> findClassAnnotation(Class<T> annotationClass,
                                                                                      Class<?> theClass) {

        List<Class<?>> hierarchy = hierarchy(theClass);

        // find in hierarchy of the classes
        for (Class<?> aClass : hierarchy) {
            T annot = aClass.getDeclaredAnnotation(annotationClass);
            if (null != annot) {
                return Optional.of(annot);
            }
        }

        return Optional.empty();
    }

    private <T extends Annotation> Optional<? extends Annotation> findMethodAnnotation(Class<T> annotationClass,
                                                                                       Class<?> theClass,
                                                                                       Method theMethod) {
        List<Class<?>> hierarchy = hierarchy(theClass);

        // find annotations in the hierarchy of methods
        String name = theMethod.getName();
        Class<?>[] parameterTypes = theMethod.getParameterTypes();

        for (Class<?> aClass : hierarchy) {
            try {
                Method method = aClass.getDeclaredMethod(name, parameterTypes);
                T annot = method.getDeclaredAnnotation(annotationClass);
                if (null != annot) {
                    return Optional.of(annot);
                }
            } catch (NoSuchMethodException ignore) {
                // ignore as the method may not be declared on some of the super classes or interfaces
            }
        }

        return Optional.empty();
    }

    private static final class ClassAnnotationKey {
        private final Class<? extends Annotation> annotationClass;
        private final Class<?> handlingClass;

        private ClassAnnotationKey(Class<? extends Annotation> annotationClass, Class<?> handlingClass) {
            this.annotationClass = annotationClass;
            this.handlingClass = handlingClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ClassAnnotationKey)) {
                return false;
            }
            ClassAnnotationKey that = (ClassAnnotationKey) o;
            return annotationClass.equals(that.annotationClass)
                    && handlingClass.equals(that.handlingClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(annotationClass, handlingClass);
        }
    }

    private static final class MethodAnnotationKey {
        private final Class<? extends Annotation> annotationClass;
        private final Class<?> definitionClass;
        private final Class<?> handlingClass;
        private final Method handlingMethod;

        private MethodAnnotationKey(Class<? extends Annotation> annotationClass,
                                    Class<?> definitionClass,
                                    Class<?> handlingClass,
                                    Method handlingMethod) {

            this.annotationClass = annotationClass;
            this.definitionClass = definitionClass;
            this.handlingClass = handlingClass;
            this.handlingMethod = handlingMethod;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MethodAnnotationKey)) {
                return false;
            }
            MethodAnnotationKey that = (MethodAnnotationKey) o;
            return annotationClass.equals(that.annotationClass)
                    && definitionClass.equals(that.definitionClass)
                    && handlingClass.equals(that.handlingClass)
                    && handlingMethod.equals(that.handlingMethod);
        }

        @Override
        public int hashCode() {
            return Objects.hash(annotationClass, definitionClass, handlingClass, handlingMethod);
        }
    }

    private static List<Class<?>> hierarchy(Class<?> aClass) {
        List<Class<?>> result = new LinkedList<>();
        Set<Class<?>> processed = new HashSet<>();

        // add the processed class
        result.add(aClass);
        // first all superclasses
        Class<?> current = aClass.getSuperclass();
        while (!Object.class.equals(current)) {
            if (processed.add(current)) {
                result.add(current);
            }
            current = current.getSuperclass();
        }

        List<Class<?>> interfaces = new LinkedList<>();

        // then all interfaces
        result.forEach(clazz -> {
            addInterfaces(clazz, interfaces, processed);
        });

        result.addAll(interfaces);

        return result;
    }

    private static void addInterfaces(Class<?> clazz, List<Class<?>> interfaces, Set<Class<?>> processed) {
        Class<?>[] found = clazz.getInterfaces();
        for (Class<?> anInterface : found) {
            if (processed.add(anInterface)) {
                interfaces.add(anInterface);
                addInterfaces(anInterface, interfaces, processed);
            }
        }
    }

    // taken from org.glassfish.jersey.server.model.internal.ModelHelper#getAnnotatedResourceClass
    private static Class<?> getDefinitionClass(Class<?> resourceClass) {
        Class<?> foundInterface = null;

        // traverse the class hierarchy to find the annotation
        // According to specification, annotation in the super-classes must take precedence over annotation in the
        // implemented interfaces
        Class<?> cls = resourceClass;
        do {
            if (cls.isAnnotationPresent(Path.class)) {
                return cls;
            }

            // if no annotation found on the class currently traversed, check for annotation in the interfaces on this
            // level - if not already previously found
            if (foundInterface == null) {
                for (final Class<?> i : cls.getInterfaces()) {
                    if (i.isAnnotationPresent(Path.class)) {
                        // store the interface reference in case no annotation will be found in the super-classes
                        foundInterface = i;
                        break;
                    }
                }
            }

            cls = cls.getSuperclass();
        } while (cls != null);

        if (foundInterface != null) {
            return foundInterface;
        }

        return resourceClass;
    }
}
