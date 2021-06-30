/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.rsocket.server;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.rsocket.server.RSocketEndpoint;
import io.helidon.rsocket.server.RSocketRouting;
import io.helidon.rsocket.server.RSocketSupport;
import io.helidon.webserver.tyrus.TyrusSupport;
import io.rsocket.Payload;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;

/**
 * Configure RSocket related things.
 */
public class RSocketCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(RSocketCdiExtension.class.getName());

    private Config config;

    private ServerCdiExtension serverCdiExtension;

    private Map<Class, Map<Annotation, Method>> methodMap = new HashMap<>();

    private Map<String, RSocketRouting.Builder> routingMap = new HashMap<>();

    /**
     * Read Configuration.
     * @param config
     */
    private void prepareRuntime(@Observes @RuntimeStart Config config) {
        this.config = config;
    }

    /**
     * Register configured RSockets.
     *
     * @param event
     * @param beanManager
     */
    private void startServer(@Observes @Priority(PLATFORM_AFTER + 99) @Initialized(ApplicationScoped.class) Object event,
                             BeanManager beanManager) {
        serverCdiExtension = beanManager.getExtension(ServerCdiExtension.class);
        wireMethods(beanManager);
        registerRSockets();
    }


    /**
     * Collect annotated endpoints.
     *
     * @param endpoint The endpoint.
     */
    private void endpointClasses(@Observes @WithAnnotations(RSocket.class) ProcessAnnotatedType<?> endpoint) {
        LOGGER.info(() -> "Annotated endpoint found " + endpoint.getAnnotatedType().getJavaClass());

        LOGGER.finest("Methods:");
        List<Method> methods = endpoint.getAnnotatedType().getMethods().stream().map(AnnotatedMethod::getJavaMember).collect(Collectors.toList());

        Map<Annotation, Method> endPointMethods = new HashMap<>();


        for (Method method : methods) {
            LOGGER.finest("Method: " + method.getName());
            LOGGER.finest("Has the following annotations");
            for (Annotation annotation : method.getAnnotations()) {
                LOGGER.finest(" - " + annotation.toString());

                if (annotation.annotationType().equals(FireAndForget.class) ||
                        annotation.annotationType().equals(RequestChannel.class) ||
                        annotation.annotationType().equals(RequestResponse.class) ||
                        annotation.annotationType().equals(RequestStream.class)
                ) {
                    endPointMethods.put(annotation, method);
                }
            }
        }

        methodMap.put(endpoint.getAnnotatedType().getJavaClass(), endPointMethods);
        LOGGER.info("Endpoints discovery completed");
    }


    /**
     * Connect found annotated classes and methods to instances.
     *
     * @param beanManager
     */
    private void wireMethods(BeanManager beanManager) {

        for (Map.Entry<Class, Map<Annotation, Method>> entry : methodMap.entrySet()) {
            Bean<?> bean = beanManager.resolve(beanManager.getBeans(entry.getKey()));
            Object rsocketInstance = lookup(bean, beanManager);

            Map<Annotation, Method> annotatedMethodMap = methodMap.get(entry.getKey());

            RSocketRouting.Builder rSocketRoutingBuilder = RSocketRouting.builder();

            for (Map.Entry<Annotation, Method> annotatedEntry : annotatedMethodMap.entrySet()) {

                Annotation annotation = annotatedEntry.getKey();
                Method method = annotatedEntry.getValue();

                if (annotation.annotationType().equals(FireAndForget.class)) {
                    rSocketRoutingBuilder.fireAndForget(((FireAndForget) annotation).route(),
                            payload -> {
                                try {
                                    return (Single<Void>) method.invoke(rsocketInstance, payload);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    LOGGER.severe(e.toString());
                                }
                                return Single.empty();
                            });

                } else if (annotation.annotationType().equals(RequestChannel.class)) {
                    rSocketRoutingBuilder.requestChannel(((RequestChannel) annotation).route(),
                            payloads -> {
                                try {
                                    return (Multi<Payload>) method.invoke(rsocketInstance, payloads);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    LOGGER.severe(e.toString());
                                }
                                return Multi.empty();
                            });
                } else if (annotation.annotationType().equals(RequestResponse.class)) {
                    rSocketRoutingBuilder.requestResponse(((RequestResponse) annotation).route(),
                            payload -> {
                                try {
                                    return (Single<Payload>) method.invoke(rsocketInstance, payload);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    LOGGER.severe(e.toString());
                                }
                                return Single.empty();
                            });
                } else if (annotation.annotationType().equals(RequestStream.class)) {
                    rSocketRoutingBuilder.requestStream(((RequestStream) annotation).route(),
                            payload -> {
                                try {
                                    return (Multi<Payload>) method.invoke(rsocketInstance, payload);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    LOGGER.severe(e.toString());
                                }
                                return Multi.empty();
                            });
                }
            }

            routingMap.put(rsocketInstance.getClass().getAnnotation(RSocket.class).path(), rSocketRoutingBuilder);
            LOGGER.info("Instance to method wiring completed!");
        }
    }

    /**
     * With the connected methods add routing to RSocket support.
     */
    private void registerRSockets() {
        RSocketSupport.Builder builder = RSocketSupport.builder();

        for (Map.Entry<String, RSocketRouting.Builder> entry : routingMap.entrySet()) {
            RSocketRouting rSocketRouting = entry.getValue().build();
            builder.register(RSocketEndpoint.create(rSocketRouting, entry.getKey())
                    .getEndPoint());
        }
        TyrusSupport rsocketSupport = builder.build();

        serverCdiExtension.serverRoutingBuilder().register("/rsocket", rsocketSupport);
    }

    @SuppressWarnings("unchecked")
    private static <T> T lookup(Bean<?> bean, BeanManager beanManager) {
        javax.enterprise.context.spi.Context context = beanManager.getContext(bean.getScope());
        Object instance = context.get(bean);
        if (instance == null) {
            CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
            instance = beanManager.getReference(bean, bean.getBeanClass(), creationalContext);
        }
        if (instance == null) {
            throw new DeploymentException("Instance of bean " + bean.getName() + " not found");
        }
        return (T) instance;
    }
}
