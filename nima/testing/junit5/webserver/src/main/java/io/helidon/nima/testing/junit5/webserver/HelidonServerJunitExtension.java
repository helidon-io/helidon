/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.testing.junit5.webserver;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.LinkedList;
import java.util.function.BiConsumer;

import io.helidon.common.LazyValue;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.ListenerConfiguration;
import io.helidon.nima.webserver.Router;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit5 extension to support Helidon Níma WebServer in tests.
 */
class HelidonServerJunitExtension implements BeforeAllCallback,
                                             AfterAllCallback,
                                             AfterEachCallback,
                                             InvocationInterceptor,
                                             ParameterResolver {

    private Class<?> testClass;
    private WebServer server;
    private LazyValue<SocketHttpClient> socketHttpClient =
            LazyValue.create(() -> SocketHttpClient.create(server.port()));
    private LazyValue<Http1Client> httpClient =
            LazyValue.create(() -> WebClient.builder()
                    .baseUri("http://localhost:" + server.port())
                    .build());
    private URI uri;

    HelidonServerJunitExtension() {
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        LogConfig.configureRuntime();

        testClass = context.getRequiredTestClass();
        ServerTest testAnnot = testClass.getAnnotation(ServerTest.class);
        if (testAnnot == null) {
            throw new IllegalStateException("Invalid test class for this extension: " + testClass);
        }

        WebServer.Builder builder = WebServer.builder()
                .port(0)
                .host("localhost");

        setupServer(builder);
        addRouting(builder);

        server = builder.start();
        uri = URI.create("http://localhost:" + server.port() + "/");
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        if (server != null) {
            server.stop();
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        if (socketHttpClient.isLoaded()) {
            socketHttpClient.get().disconnect();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        Class<?> paramType = parameterContext.getParameter().getType();
        if (paramType.equals(Http1Client.class)) {
            return true;
        }
        if (paramType.equals(SocketHttpClient.class)) {
            return true;
        }
        if (paramType.equals(WebServer.class)) {
            return true;
        }
        if (paramType.equals(URI.class)) {
            return true;
        }
        // todo maybe use context
        // return Context.singletonContext().value(GenericType.create(paramType)).isPresent();
        return false;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        Class<?> paramType = parameterContext.getParameter().getType();
        if (paramType.equals(SocketHttpClient.class)) {
            Socket socketAnnot = parameterContext.getParameter().getAnnotation(Socket.class);

            if (socketAnnot != null) {
                String portName = socketAnnot.value();
                socketHttpClient = LazyValue.create(() -> SocketHttpClient.create(server.port(portName)));
            }

            return socketHttpClient.get();
        }
        if (paramType.equals(Http1Client.class)) {

            Socket socketAnnot = parameterContext.getParameter().getAnnotation(Socket.class);

            if (socketAnnot != null) {
                String portName = socketAnnot.value();
                httpClient = LazyValue.create(() -> WebClient.builder()
                                .baseUri("http://localhost:" + server.port(portName))
                                .build());
            }

            return httpClient.get();
        }
        if (paramType.equals(WebServer.class)) {
            return server;
        }
        if (paramType.equals(URI.class)) {
            return uri;
        }
        // todo maybe use context
        //return Context.singletonContext().value(GenericType.create(paramType)).orElse(null);
        return false;
    }

    private void setupServer(WebServer.Builder builder) {
        withStaticMethods(SetUpServer.class, (setUpServer, method) -> {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpServer.class.getSimpleName()
                                                           + " does not have exactly one parameter (Server.Builder)");
            }
            if (!parameterTypes[0].equals(WebServer.Builder.class)) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpServer.class.getSimpleName()
                                                           + " does not have exactly one parameter (Server.Builder)");
            }
            try {
                method.setAccessible(true);
                method.invoke(null, builder);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Could not invoke method " + method, e);
            }
        });
    }

    private <T extends Annotation> void withStaticMethods(Class<T> annotationType, BiConsumer<T, Method> handler) {
        LinkedList<Class<?>> hierarchy = new LinkedList<>();
        Class<?> analyzedClass = testClass;
        while (analyzedClass != null && !analyzedClass.equals(Object.class)) {
            hierarchy.addFirst(analyzedClass);
            analyzedClass = analyzedClass.getSuperclass();
        }
        for (Class<?> aClass : hierarchy) {
            for (Method method : aClass.getDeclaredMethods()) {
                T annotation = method.getDeclaredAnnotation(annotationType);
                if (annotation != null) {
                    // maybe our method
                    if (Modifier.isStatic(method.getModifiers())) {
                        handler.accept(annotation, method);
                    } else {
                        throw new IllegalStateException("Method " + method + " is annotated with "
                                                                + annotationType.getSimpleName()
                                                                + " yet it is not static");
                    }
                }
            }
        }
    }

    private void addRouting(WebServer.Builder builder) {
        withStaticMethods(SetUpRoute.class, (annotation, method) -> {
            // validate parameters
            String socketName = annotation.value();
            boolean isDefaultSocket = socketName.equals(WebServer.DEFAULT_SOCKET_NAME);
            // allowed parameters are Router.Builder and ListenerConfiguration.Builder
            BiConsumer<ListenerConfiguration.Builder, Router.RouterBuilder<?>> methodConsumer
                    = createRoutingMethodCall(isDefaultSocket, method);

            if (isDefaultSocket) {
                builder.defaultSocket(socketBuilder -> {
                    methodConsumer.accept(socketBuilder, builder);
                });
            } else {
                builder.socket(socketName, methodConsumer);
            }
        });
    }

    private BiConsumer<ListenerConfiguration.Builder, Router.RouterBuilder<?>> createRoutingMethodCall(boolean isDefaultSocket,
                                                                                                       Method method) {
        // default socket cannot have socket configuration
        BiConsumer<Object[], ListenerConfiguration.Builder> socketConsumer = null;
        BiConsumer<Object[], Router.RouterBuilder<?>> routingConsumer = null;
        BiConsumer<Object[], HttpRules> httpRoutingConsumer = null;

        Class<?>[] parameterTypes = method.getParameterTypes();
        int parameterCount = parameterTypes.length;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];

            if (parameterType.equals(Router.RouterBuilder.class)) {
                if (routingConsumer == null) {
                    int index = i;
                    routingConsumer = (params, routing) -> params[index] = routing;
                } else {
                    throw new IllegalArgumentException("Method " + method + " has more than one router builder parameters");
                }
            } else if (parameterType.equals(ListenerConfiguration.Builder.class)) {
                if (isDefaultSocket) {
                    throw new IllegalArgumentException("Method " + method + " configures default socket and is not allowed "
                                                               + "to use ListenerConfiguration builder.");
                }
                if (socketConsumer == null) {
                    int index = i;
                    socketConsumer = (params, socket) -> params[index] = socket;
                }
            } else if (HttpRules.class.isAssignableFrom(parameterType)) {
                if (httpRoutingConsumer == null) {
                    int index = i;
                    httpRoutingConsumer = (params, routing) -> params[index] = routing;
                } else {
                    throw new IllegalArgumentException("Method " + method + " has more than one router builder parameters");
                }
            }

        }

        if (socketConsumer == null) {
            socketConsumer = (params, socket) -> {
            };
        }
        if (routingConsumer == null) {
            routingConsumer = (params, routing) -> {
            };
        }

        BiConsumer<Object[], ListenerConfiguration.Builder> theSocketConsumer = socketConsumer;

        if (httpRoutingConsumer == null) {
            BiConsumer<Object[], Router.RouterBuilder<?>> theRoutingConsumer = routingConsumer;

            // we need to find the method parameters
            return (socketConfig, routing) -> {
                Object[] parameters = new Object[parameterCount];
                theSocketConsumer.accept(parameters, socketConfig);
                theRoutingConsumer.accept(parameters, routing);
                try {
                    method.setAccessible(true);
                    method.invoke(null, parameters);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Cannot invoke router/socket method", e);
                }
            };
        } else {
            BiConsumer<Object[], HttpRules> theHttpConsumer = httpRoutingConsumer;

            // we need to find the method parameters
            return (socketConfig, routing) -> {
                Object[] parameters = new Object[parameterCount];
                theSocketConsumer.accept(parameters, socketConfig);
                HttpRouting.Builder httpBuilder = HttpRouting.builder();
                theHttpConsumer.accept(parameters, httpBuilder);
                try {
                    method.setAccessible(true);
                    method.invoke(null, parameters);
                    routing.addRouting(httpBuilder);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Cannot invoke router/socket method", e);
                }
            };
        }
    }

}
