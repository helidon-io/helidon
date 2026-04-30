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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.glassfish.jersey.model.AnnotatedMethod;

class ParticipantImpl implements Participant {

    private static final Logger LOGGER = Logger.getLogger(ParticipantImpl.class.getName());

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

    private final Map<Class<? extends Annotation>, URI> compensatorLinks = new HashMap<>();
    private final Map<Class<? extends Annotation>, Set<Method>> methodMap;

    ParticipantImpl(URI baseUri, String contextPath, Class<?> resourceClazz) {
        methodMap = scanForLRAMethods(resourceClazz);
        methodMap.entrySet().stream()
                // Looking only for participant methods
                .filter(e -> e.getKey() != LRA.class)
                .forEach(e -> {
                    Set<Method> methods = e.getValue();
                    Method method = methods.stream().iterator().next();
                    if (methods.size() > 1) {
                        LOGGER.log(Level.WARNING,
                                "LRA participant {0} contains more then one @{1} method!",
                                new Object[] {method.getDeclaringClass().getName(),
                                        e.getKey().getSimpleName()}
                        );
                    }

                    if (isNonJaxRsParticipantMethod(resourceClazz, method)) {
                        if (!NON_JAX_RS_PARTICIPANT_CALLBACKS.containsValue(e.getKey())) {
                            return;
                        }
                        //no jax-rs method
                        URI uri = UriBuilder.fromUri(baseUri)
                                .path(contextPath) //Auxiliary non Jax-Rs resource
                                .path(callbackPath(e.getKey()))//@Complete -> /complete
                                .path(resourceClazz.getName())
                                .path(method.getName())
                                .build();
                        compensatorLinks.put(e.getKey(), uri);
                        return;
                    }

                    UriBuilder builder = UriBuilder.fromUri(baseUri)
                            .path(resourceClazz);

                    jaxRsMethod(resourceClazz, method)
                            .map(m -> m.getAnnotation(Path.class))
                            .map(Path::value)
                            .ifPresent(builder::path);

                    URI uri = builder.build();
                    compensatorLinks.put(e.getKey(), uri);
                });
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

    private static Optional<AnnotatedMethod> jaxRsMethod(Class<?> resourceClass, Method method) {
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
                .collect(Collectors.toList());

        if (found.size() == 0) {
            Optional<Annotation> inheritedMethodAnnotation = inheritedLraAnnotation(m.getDeclaringClass(), m);
            if (inheritedMethodAnnotation.isPresent()) {
                return inheritedMethodAnnotation;
            }
            if (!m.isBridge() && !m.isSynthetic()) {
                for (Method bridgeMethod : m.getDeclaringClass().getDeclaredMethods()) {
                    if (!bridgeMethod.isBridge() && !bridgeMethod.isSynthetic()) {
                        continue;
                    }
                    if (!bridgeMethod.getName().equals(m.getName())
                            || bridgeMethod.getParameterCount() != m.getParameterCount()) {
                        continue;
                    }
                    Class<?>[] bridgeParameters = bridgeMethod.getParameterTypes();
                    Class<?>[] parameters = m.getParameterTypes();
                    boolean bridgedMethod = true;
                    for (int i = 0; i < parameters.length; i++) {
                        if (!bridgeParameters[i].isAssignableFrom(parameters[i])) {
                            bridgedMethod = false;
                            break;
                        }
                    }
                    if (!bridgedMethod) {
                        continue;
                    }
                    Optional<Annotation> bridgeAnnotation = Arrays.stream(bridgeMethod.getDeclaredAnnotations())
                            .filter(a -> LRA_ANNOTATIONS.contains(a.annotationType()))
                            .findFirst()
                            .or(() -> inheritedLraAnnotation(bridgeMethod.getDeclaringClass(), bridgeMethod));
                    if (bridgeAnnotation.isPresent()) {
                        return bridgeAnnotation;
                    }
                }
            }

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

    private static Optional<Annotation> inheritedLraAnnotation(Class<?> clazz, Method method) {
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            Optional<Annotation> annotation = lraAnnotation(superclass, method)
                    .or(() -> inheritedLraAnnotation(superclass, method));
            if (annotation.isPresent()) {
                return annotation;
            }
        }

        for (Class<?> interfaceClass : clazz.getInterfaces()) {
            Optional<Annotation> annotation = lraAnnotation(interfaceClass, method)
                    .or(() -> inheritedLraAnnotation(interfaceClass, method));
            if (annotation.isPresent()) {
                return annotation;
            }
        }

        return Optional.empty();
    }

    private static Optional<Annotation> lraAnnotation(Class<?> clazz, Method method) {
        try {
            Method inheritedMethod = clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
            return Arrays.stream(inheritedMethod.getDeclaredAnnotations())
                    .filter(a -> LRA_ANNOTATIONS.contains(a.annotationType()))
                    .findFirst();
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    static Map<Class<? extends Annotation>, Set<Method>> scanForLRAMethods(Class<?> clazz) {
        Map<Class<? extends Annotation>, Set<Method>> methods = new HashMap<>();
        Set<String> scannedSignatures = new HashSet<>();
        Class<?> resourceClass = clazz;
        do {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isBridge() || m.isSynthetic()) {
                    continue;
                }
                String methodSignature = methodSignature(m);
                if (!scannedSignatures.add(methodSignature)) {
                    continue;
                }
                Optional<Annotation> annotation = getLRAAnnotation(m);
                if (annotation.isPresent()) {
                    var annotationType = annotation.get().annotationType();
                    methods.putIfAbsent(annotationType, new LinkedHashSet<>());
                    methods.get(annotationType).add(m);
                }
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);
        scanDefaultInterfaceLRAMethods(resourceClass, scannedSignatures, methods);
        return methods;
    }

    private static void scanDefaultInterfaceLRAMethods(Class<?> clazz,
                                                       Set<String> scannedSignatures,
                                                       Map<Class<? extends Annotation>, Set<Method>> methods) {
        if (clazz == null) {
            return;
        }
        if (clazz.isInterface()) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (!m.isDefault() || m.isBridge() || m.isSynthetic()) {
                    continue;
                }
                String methodSignature = methodSignature(m);
                if (!scannedSignatures.add(methodSignature)) {
                    continue;
                }
                Optional<Annotation> annotation = getLRAAnnotation(m);
                if (annotation.isPresent()) {
                    var annotationType = annotation.get().annotationType();
                    methods.putIfAbsent(annotationType, new LinkedHashSet<>());
                    methods.get(annotationType).add(m);
                }
            }
        }
        for (Class<?> interfaceClass : clazz.getInterfaces()) {
            scanDefaultInterfaceLRAMethods(interfaceClass, scannedSignatures, methods);
        }
        scanDefaultInterfaceLRAMethods(clazz.getSuperclass(), scannedSignatures, methods);
    }

    private static String methodSignature(Method method) {
        return method.getName() + Arrays.toString(method.getParameterTypes());
    }

    @Override
    public String toString() {
        return "ParticipantImpl{"
                + this.complete()
                .or(this::compensate)
                .or(this::after)
                .map(URI::toASCIIString)
                .orElse(null)
                + "}";
    }
}
