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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A model of an annotated gRPC method.
 */
public class AnnotatedMethod implements AnnotatedElement {

    /**
     * The set of meta-annotations that are used to identify an annotated gRPC method.
     */
    private static final Set<Class<? extends Annotation>> METHOD_META_ANNOTATIONS
            = Set.of(GrpcMethod.class);

    /**
     * The set of annotations that are used to identify an annotated gRPC method.
     */
    private static final Set<Class<? extends Annotation>> METHOD_ANNOTATIONS
            = Set.of(GrpcMethod.class,
                     Bidirectional.class,
                     ClientStreaming.class,
                     ServerStreaming.class,
                     Unary.class);

    /**
     * The set of method parameter annotations that are used to identify an annotated gRPC method.
     */
    private static final Set<Class<? extends Annotation>> PARAMETER_ANNOTATIONS = Set.of();

    /**
     * The declared method this {@link AnnotatedMethod} represents.
     */
    private final Method declaredMethod;

    /**
     * The actual annotated gRPC method.
     * <p>
     * This may be the same as or overridden by the {@link #declaredMethod}.
     */
    private final Method actualMethod;

    /**
     * The annotations present on the method.
     * <p>
     * This is a merged set of annotations from both the declared and actual methods.
     */
    private final Annotation[] methodAnnotations;

    /**
     * The annotations present on the method's parameters.
     * <p>
     * This is a merged set of annotations from both the declared and actual methods.
     */
    private final Annotation[][] parameterAnnotations;

    /**
     * Create annotated method instance from a {@link Method Java method}.
     *
     * @param method the Java method
     */
    private AnnotatedMethod(Method method) {
        this.declaredMethod = method;
        this.actualMethod = findAnnotatedMethod(method);

        if (method.equals(actualMethod)) {
            methodAnnotations = method.getAnnotations();
            parameterAnnotations = method.getParameterAnnotations();
        } else {
            methodAnnotations = mergeMethodAnnotations(method, actualMethod);
            parameterAnnotations = mergeParameterAnnotations(method, actualMethod);
        }
    }

    /**
     * Create an {@link AnnotatedMethod} instance from a {@link Method Java method}.
     *
     * @param method the Java method
     * @throws java.lang.NullPointerException if the method parameter is null
     * @return an {@link AnnotatedMethod} instance representing the Java method
     */
     public static AnnotatedMethod create(Method method) {
         return new AnnotatedMethod(Objects.requireNonNull(method));
     }

    /**
     * Get the underlying Java method.
     * <p>
     * This will be the method that is actually annotated with {@link GrpcMethod},
     * which may be the same as or overridden by the method returned by {@link #declaredMethod()}.
     *
     * @return the actual annotated Java method.
     */
    public Method method() {
        return actualMethod;
    }

    /**
     * Get the declared Java method.
     * <p>
     * This method overrides, or is the same as, the one retrieved by {@link #method()}.
     *
     * @return the declared Java method.
     */
    public Method declaredMethod() {
        return declaredMethod;
    }

    /**
     * Get method parameter annotations.
     *
     * @return method parameter annotations.
     */
    public Annotation[][] parameterAnnotations() {
        return parameterAnnotations.clone();
    }

    /**
     * Get method parameter types.
     *
     * See also {@link Method#getParameterTypes()}.
     *
     * @return method parameter types.
     */
    public Class<?>[] parameterTypes() {
        return actualMethod.getParameterTypes();
    }

    /**
     * Get method type parameters.
     *
     * See also {@link Method#getTypeParameters()}.
     *
     * @return method type parameters.
     */
    public TypeVariable<Method>[] typeParameters() {
        return actualMethod.getTypeParameters();
    }

    /**
     * Get generic method parameter types.
     *
     * See also {@link Method#getGenericParameterTypes()}.
     *
     * @return generic method parameter types.
     */
    public Type[] genericParameterTypes() {
        return actualMethod.getGenericParameterTypes();
    }

    /**
     * Get generic method return type.
     *
     * See also {@link Method#getGenericReturnType()} ()}.
     *
     * @return generic method return types.
     */
    public Type genericReturnType() {
        return actualMethod.getGenericReturnType();
    }

    /**
     * Get method return type.
     *
     * See also {@link Method#getReturnType()} ()} ()}.
     *
     * @return method return types.
     */
    public Class<?> returnType() {
        return actualMethod.getReturnType();
    }

    /**
     * Get all instances of the specified meta-annotation type found on the method
     * annotations (a meta-annotation is an annotation that annotates other
     * annotations).
     *
     * @param annotation meta-annotation class to be searched for.
     * @param <T>        meta-annotation type.
     *
     * @return meta-annotation instances of a given type annotating the method
     *         annotations.
     */
    public <T extends Annotation> Stream<T> metaMethodAnnotations(Class<T> annotation) {
        return Arrays.stream(methodAnnotations)
                     .map(ann -> ann.annotationType().getAnnotation(annotation))
                     .filter(Objects::nonNull);
    }

    /**
     * Get the first of the specified meta-annotation type found on the method
     * annotations or on the method itself (a meta-annotation is an annotation that
     * annotates other annotations).
     *
     * @param type  meta-annotation class to be searched for.
     * @param <T>   meta-annotation type.
     *
     * @return meta-annotation instances of a given type annotating the method
     *         annotations
     */
    public <T extends Annotation> T firstAnnotationOrMetaAnnotation(Class<T> type) {
        return annotationOrMetaAnnotation(type).findFirst().orElse(null);
    }

    /**
     * Get all instances of the specified meta-annotation type found on the method
     * annotations or on the method itself (a meta-annotation is an annotation that
     * annotates other annotations).
     *
     * @param type  meta-annotation class to be searched for.
     * @param <T>   meta-annotation type.
     *
     * @return meta-annotation instances of a given type annotating the method
     *         annotations
     */
    public <T extends Annotation> Stream<T> annotationOrMetaAnnotation(Class<T> type) {
        return Arrays.stream(methodAnnotations)
                     .map(ann -> annotationOrMetaAnnotation(type, ann))
                     .filter(Objects::nonNull);
    }

    /**
     * Get all instances of annotations annotated with the specified meta-annotation.
     *
     * @param type  meta-annotation class to be searched for.
     *
     * @return all instances of annotations annotated with the specified meta-annotation
     */
    public Stream<Annotation> annotationsWithMetaAnnotation(Class<? extends Annotation> type) {
        return Arrays.stream(methodAnnotations)
                     .filter(ann -> ann.annotationType().isAnnotationPresent(type));
    }

    @SuppressWarnings("unchecked")
    private <T extends Annotation> T annotationOrMetaAnnotation(Class<T> type, Annotation annotation) {
        if (annotation.annotationType().equals(type)) {
            return (T) annotation;
        } else {
            return annotation.annotationType().getAnnotation(type);
        }
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return Arrays.stream(methodAnnotations)
                .filter(ma -> ma.annotationType() == annotationType)
                .map(annotationType::cast)
                .findFirst()
                .orElse(actualMethod.getAnnotation(annotationType));
    }

    @Override
    public Annotation[] getAnnotations() {
        return methodAnnotations.clone();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return getAnnotations();
    }

    @Override
    public String toString() {
        return declaredMethod.toString();
    }

    /**
     * Merge the annotations from two methods.
     * <p>
     * Where both methods have the same annotation then the annotation from the
     * declared method will be used.
     *
     * @param declaredMethod  the declared method
     * @param actualMethod    the method that the declared method overrides
     * @return  an array of merged annotations
     */
    private static Annotation[] mergeMethodAnnotations(Method declaredMethod, Method actualMethod) {
        List<Annotation> list = new ArrayList<>(Arrays.asList(declaredMethod.getAnnotations()));

        Arrays.stream(actualMethod.getAnnotations())
                .filter(a -> !declaredMethod.isAnnotationPresent(a.getClass()))
                .forEach(list::add);

        return list.toArray(new Annotation[0]);
    }

    /**
     * Merge the parameter annotations from two methods.
     * <p>
     * Where a parameter has the same annotation in both methods
     * then the annotation from the declared method will be used.
     *
     * @param declaredMethod  the declared method
     * @param actualMethod    the method that the declared method overrides
     * @return  an array of merged annotations
     */
    private static Annotation[][] mergeParameterAnnotations(Method declaredMethod, Method actualMethod) {
        Annotation[][] methodParamAnnotations = declaredMethod.getParameterAnnotations();
        Annotation[][] annotatedMethodParamAnnotations = actualMethod.getParameterAnnotations();

        List<List<Annotation>> methodParamAnnotationsList = new ArrayList<>();

        for (int i = 0; i < methodParamAnnotations.length; i++) {
            List<Annotation> al = Arrays.asList(methodParamAnnotations[i]);
            for (Annotation a : annotatedMethodParamAnnotations[i]) {
                if (annotationNotInList(a.getClass(), al)) {
                    al.add(a);
                }
            }
            methodParamAnnotationsList.add(al);
        }

        Annotation[][] mergedAnnotations = new Annotation[methodParamAnnotations.length][];
        for (int i = 0; i < methodParamAnnotations.length; i++) {
            List<Annotation> paramAnnotations = methodParamAnnotationsList.get(i);
            mergedAnnotations[i] = paramAnnotations.toArray(new Annotation[0]);
        }

        return mergedAnnotations;
    }

    private static boolean annotationNotInList(Class<? extends Annotation> type, List<Annotation> annotations) {
        return annotations.stream().noneMatch(annotation -> type == annotation.getClass());
    }

    /**
     * Find the actual annotated gRPC method given a declared method.
     * <p>
     * A declared method may itself be annotated with gRPC method annotations
     * or it may override a method annotated with gRPC method annotations.
     * If the declared method is an annotated gRPC method then it will be returned,
     * if not then the first overridden annotated gRPC method in the class hierarchy
     * will be returned or if no method in the class hierarchy is annotated then the
     * declared method will be returned.
     * <p>
     * The search order for finding an overridden annotated method is to search the
     * class hierarchy before searching the implemented interfaces.
     *
     * @param declaredMethod  the declared method
     * @return  the actual annotated gRPC method or the declared method if no
     *          method in the class hierarchy is annotated
     */
    private static Method findAnnotatedMethod(Method declaredMethod) {
        Method am = findAnnotatedMethod(declaredMethod.getDeclaringClass(), declaredMethod);
        return (am != null) ? am : declaredMethod;
    }

    /**
     * Find the actual annotated gRPC method given a declared method.
     * <p>
     * A declared method may itself be annotated with gRPC method annotations
     * or it may override a method annotated with gRPC method annotations.
     * If the declared method is an annotated gRPC method then it will be returned,
     * if not then the first overridden annotated gRPC method in the class hierarchy
     * will be returned or if no method in the class hierarchy is annotated then the
     * declared method will be returned.
     * <p>
     * The search order for finding an overridden annotated method is to search the
     * class hierarchy before searching the implemented interfaces.
     *
     * @param declaringClass  the Class declaring the method
     * @param declaredMethod  the declared method
     * @return  the actual annotated gRPC method or the declared method if no
     *          method in the class hierarchy is annotated
     */
    private static Method findAnnotatedMethod(Class<?> declaringClass, Method declaredMethod) {
        if (declaringClass == Object.class) {
            return null;
        }

        declaredMethod = AccessController.doPrivileged(ModelHelper.findMethodOnClassPA(declaringClass, declaredMethod));
        if (declaredMethod == null) {
            return null;
        }

        if (hasAnnotations(declaredMethod)) {
            return declaredMethod;
        }

        // Super classes take precedence over interfaces
        Class<?> sc = declaringClass.getSuperclass();
        if (sc != null && sc != Object.class) {
            Method sm = findAnnotatedMethod(sc, declaredMethod);
            if (sm != null) {
                return sm;
            }
        }

        for (Class<?> ic : declaringClass.getInterfaces()) {
            Method im = findAnnotatedMethod(ic, declaredMethod);
            if (im != null) {
                return im;
            }
        }

        return null;
    }

    /**
     * Determine whether a method is annotated with any of the annotations,
     * meta-annotations or parameter annotations that would make it a recognised
     * gRPC method.
     *
     * @param method  the {@link Method} to test
     * @return  {@code true} if the method is an annotated gRPC method
     */
    private static boolean hasAnnotations(Method method) {
        return hasMetaMethodAnnotations(method)
                || hasMethodAnnotations(method)
                || hasParameterAnnotations(method);
    }

    /**
     * Determine whether a method is annotated with any of the meta-annotations
     * that would make it a recognised gRPC method.
     *
     * @param method  the {@link Method} to test
     * @return  {@code true} if the method is an annotated gRPC method
     */
    private static boolean hasMetaMethodAnnotations(Method method) {
        return METHOD_META_ANNOTATIONS.stream()
                .anyMatch(a -> hasMetaAnnotation(method, a));
    }

    /**
     * Determine whether any of a method's annotations are themselves annotated
     * with a specific annotation.
     *
     * @param method  the method to test
     * @param type    the type of the meta-annotation to search for
     * @return  {@link true} if any of the method's annotations have the
     *          specified meta-annotation
     */
    private static boolean hasMetaAnnotation(Method method, Class<? extends Annotation> type) {
        return Arrays.stream(method.getAnnotations())
                     .anyMatch(a -> a.annotationType().isAnnotationPresent(type));
    }

    /**
     * Determine whether a method is annotated with any of the annotations
     * that would make it a recognised gRPC method.
     *
     * @param method  the {@link Method} to test
     * @return  {@code true} if the method is an annotated gRPC method
     */
    private static boolean hasMethodAnnotations(Method method) {
        return METHOD_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent);
    }

    /**
     * Determine whether a method has parameters that are annotated with any of
     * the annotations that would make it a recognised gRPC method.
     *
     * @param method  the {@link Method} to test
     * @return  {@code true} if the method is an annotated gRPC method
     */
    private static boolean hasParameterAnnotations(Method method) {
        return Arrays.stream(method.getParameterAnnotations())
                .flatMap(Arrays::stream)
                .anyMatch(a -> PARAMETER_ANNOTATIONS.contains(a.annotationType()));
    }
}
