/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.glassfish.jersey.model.AnnotatedMethod;

final class ParticipantFactory {

    private static final System.Logger LOGGER = System.getLogger(ParticipantFactory.class.getName());

    private final Map<Class<? extends Annotation>, Set<Method>> methodMap;
    private final Map<Class<? extends Annotation>, URI> fixedLinks;
    private final Map<Class<? extends Annotation>, NonJaxRsCallback> nonJaxRsCallbacks;
    private final ParticipantImpl fixedParticipant;

    ParticipantFactory(URI baseUri, String contextPath, Class<?> resourceClass) {
        this(baseUri, contextPath, resourceClass, methods(resourceClass));
    }

    ParticipantFactory(URI baseUri,
                       String contextPath,
                       Class<?> resourceClass,
                       Map<Class<? extends Annotation>, Set<Method>> methodMap) {
        this.methodMap = methodMap;

        Map<Class<? extends Annotation>, URI> links = new HashMap<>();
        Map<Class<? extends Annotation>, NonJaxRsCallback> callbacks = new HashMap<>();
        methodMap.entrySet().stream()
                .filter(entry -> entry.getKey() != LRA.class)
                .forEach(entry -> {
                    Set<Method> annotatedMethods = entry.getValue();
                    Method method = annotatedMethods.iterator().next();
                    if (annotatedMethods.size() > 1) {
                        LOGGER.log(Level.WARNING,
                                   "LRA participant {0} contains more then one @{1} method!",
                                   new Object[] {method.getDeclaringClass().getName(),
                                           entry.getKey().getSimpleName()});
                    }

                    Optional<AnnotatedMethod> jaxRsMethod = ParticipantImpl.jaxRsMethod(resourceClass, method);
                    if (jaxRsMethod.isEmpty()) {
                        String callbackType = ParticipantImpl.callbackPath(entry.getKey());
                        URI callbackUri = UriBuilder.fromUri(baseUri)
                                .path(contextPath)
                                .path(callbackType)
                                .path(resourceClass.getName())
                                .path(method.getName())
                                .build();
                        callbacks.put(entry.getKey(),
                                      new NonJaxRsCallback(callbackType,
                                                          resourceClass.getName(),
                                                          method.getName(),
                                                          callbackUri));
                        return;
                    }

                    UriBuilder builder = UriBuilder.fromUri(baseUri)
                            .path(resourceClass);
                    Optional.ofNullable(jaxRsMethod.get().getAnnotation(Path.class))
                            .map(Path::value)
                            .ifPresent(builder::path);
                    links.put(entry.getKey(), builder.build());
                });

        fixedLinks = Map.copyOf(links);
        nonJaxRsCallbacks = Map.copyOf(callbacks);
        fixedParticipant = nonJaxRsCallbacks.isEmpty() ? new ParticipantImpl(fixedLinks, methodMap) : null;
    }

    static Map<Class<? extends Annotation>, Set<Method>> methods(Class<?> resourceClass) {
        Map<Class<? extends Annotation>, Set<Method>> methods = new HashMap<>();
        ParticipantImpl.scanForLRAMethods(resourceClass)
                .forEach((annotation, annotatedMethods) -> methods.put(annotation, Set.copyOf(annotatedMethods)));
        return Map.copyOf(methods);
    }

    ParticipantImpl participant(URI lraId, NonJaxRsCallbackAuthenticator callbackAuthenticator) {
        if (fixedParticipant != null) {
            return fixedParticipant;
        }

        Map<Class<? extends Annotation>, URI> links = new HashMap<>(fixedLinks);
        nonJaxRsCallbacks.forEach((annotation, callback) -> {
            URI callbackUri = callback.uri();
            if (!callbackAuthenticator.compatibilityMode()) {
                callbackUri = UriBuilder.fromUri(callbackUri)
                        .replaceQueryParam(NonJaxRsCallbackAuthenticator.CAPABILITY_QUERY_PARAMETER,
                                           callbackAuthenticator.capability(lraId,
                                                                            callback.callbackType(),
                                                                            callback.resourceClassName(),
                                                                            callback.methodName()))
                        .build();
            }
            links.put(annotation, callbackUri);
        });
        return new ParticipantImpl(links, methodMap);
    }

    private record NonJaxRsCallback(String callbackType,
                                    String resourceClassName,
                                    String methodName,
                                    URI uri) {
    }
}
