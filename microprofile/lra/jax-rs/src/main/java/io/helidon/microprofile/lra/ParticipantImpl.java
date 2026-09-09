/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.microprofile.lra;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.lra.coordinator.client.Participant;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.glassfish.jersey.model.AnnotatedMethod;

class ParticipantImpl implements Participant {

    static final Set<Class<? extends Annotation>> LRA_ANNOTATIONS =
            Set.of(
                    LRA.class,
                    Compensate.class,
                    Complete.class,
                    Forget.class,
                    Status.class,
                    AfterLRA.class,
                    Leave.class
            );

    static final Set<Class<? extends Annotation>> JAX_RS_METHOD_ANNOTATIONS =
            Set.of(
                    Path.class,
                    Consumes.class,
                    Produces.class
            );

    static final Set<Class<? extends Annotation>> JAX_RS_PARAMETER_ANNOTATIONS =
            Set.of(
                    Context.class,
                    Encoded.class,
                    DefaultValue.class,
                    MatrixParam.class,
                    QueryParam.class,
                    CookieParam.class,
                    HeaderParam.class,
                    PathParam.class,
                    FormParam.class
            );

    static final Map<String, Class<? extends Annotation>> NON_JAX_RS_PARTICIPANT_CALLBACKS =
            Map.of(
                    callbackPath(Compensate.class), Compensate.class,
                    callbackPath(Complete.class), Complete.class,
                    callbackPath(Forget.class), Forget.class,
                    callbackPath(Status.class), Status.class,
                    callbackPath(AfterLRA.class), AfterLRA.class
            );

    private final Map<Class<? extends Annotation>, URI> compensatorLinks;
    private final Map<Class<? extends Annotation>, Set<Method>> methodMap;

    ParticipantImpl(Map<Class<? extends Annotation>, URI> compensatorLinks,
                    Map<Class<? extends Annotation>, Set<Method>> methodMap) {
        this.compensatorLinks = Map.copyOf(compensatorLinks);
        this.methodMap = methodMap;
    }

    boolean isLraMethod(Method m) {
        return methodMap.values().stream().flatMap(Collection::stream).anyMatch(m::equals);
    }

    static boolean isNonJaxRsParticipantMethod(Class<?> resourceClass, Method method) {
        return jaxRsMethod(resourceClass, method).isEmpty();
    }

    private static boolean hasJaxRsAnnotation(AnnotatedMethod method) {
        return Arrays.stream(method.getAnnotations())
                .map(Annotation::annotationType)
                .anyMatch(annotationType -> JAX_RS_METHOD_ANNOTATIONS.contains(annotationType)
                        || annotationType.isAnnotationPresent(HttpMethod.class))
                || Arrays.stream(method.getParameterAnnotations())
                        .flatMap(Arrays::stream)
                        .map(Annotation::annotationType)
                        .anyMatch(JAX_RS_PARAMETER_ANNOTATIONS::contains);
    }

    private static boolean canBeJaxRsCallback(AnnotatedMethod method) {
        return method.isAnnotationPresent(Path.class)
                || Arrays.stream(method.getAnnotations())
                        .map(Annotation::annotationType)
                        .anyMatch(annotationType -> annotationType.isAnnotationPresent(HttpMethod.class));
    }

    static Optional<AnnotatedMethod> jaxRsMethod(Class<?> resourceClass, Method method) {
        Method callbackMethod = method;
        try {
            callbackMethod = resourceClass.getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            // Use the scanned LRA method.
        }

        AnnotatedMethod annotatedMethod = new AnnotatedMethod(callbackMethod);
        if (!hasJaxRsAnnotation(annotatedMethod)) {
            return Optional.empty();
        }

        return canBeJaxRsCallback(annotatedMethod)
                ? Optional.of(annotatedMethod)
                : Optional.empty();
    }

    static String callbackPath(Class<? extends Annotation> annotationType) {
        return annotationType.getSimpleName().toLowerCase();
    }

    public Optional<URI> compensate() {
        return Optional.ofNullable(compensatorLinks.get(Compensate.class));
    }

    public Optional<URI> complete() {
        return Optional.ofNullable(compensatorLinks.get(Complete.class));
    }

    public Optional<URI> forget() {
        return Optional.ofNullable(compensatorLinks.get(Forget.class));
    }

    public Optional<URI> leave() {
        return Optional.ofNullable(compensatorLinks.get(Leave.class));
    }

    public Optional<URI> after() {
        return Optional.ofNullable(compensatorLinks.get(AfterLRA.class));
    }

    public Optional<URI> status() {
        return Optional.ofNullable(compensatorLinks.get(Status.class));
    }

    static Optional<Annotation> getLRAAnnotation(Method m) {
        List<Annotation> found = Arrays.stream(m.getDeclaredAnnotations())
                .filter(a -> LRA_ANNOTATIONS.contains(a.annotationType()))
                .toList();

        if (found.isEmpty()) {
            // LRA can be inherited from class or its predecessors
            var clazz = m.getDeclaringClass();
            do {
                LRA clazzLraAnnotation = clazz.getAnnotation(LRA.class);
                if (clazzLraAnnotation != null) {
                    return Optional.of(clazzLraAnnotation);
                }
                clazz = clazz.getSuperclass();
            } while (clazz != null);
        }

        return found.stream().findFirst();
    }

    static Map<Class<? extends Annotation>, Set<Method>> scanForLRAMethods(Class<?> clazz) {
        Map<Class<? extends Annotation>, Set<Method>> methods = new HashMap<>();
        do {
            for (Method m : clazz.getDeclaredMethods()) {
                Optional<Annotation> annotation = getLRAAnnotation(m);
                if (annotation.isPresent()) {
                    var annotationType = annotation.get().annotationType();
                    methods.putIfAbsent(annotationType, new HashSet<>());
                    methods.get(annotationType).add(m);
                }
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);
        return methods;
    }

    @Override
    public String toString() {
        return "ParticipantImpl{"
                + this.complete()
                .or(this::compensate)
                .or(this::after)
                .map(URI::getRawPath)
                .orElse(null)
                + "}";
    }
}
