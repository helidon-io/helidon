/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.testing.virtualthreads.PinningRecorder;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.LazyConfigSource;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Services;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.WebServerService__ServiceDescriptor;
import io.helidon.webserver.testing.junit5.spi.ServerJunitExtension;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import static io.helidon.webserver.WebServer.DEFAULT_SOCKET_NAME;
import static io.helidon.webserver.testing.junit5.Junit5Util.withStaticMethods;

/**
 * JUnit5 extension to support Helidon WebServer in tests.
 */
class HelidonServerJunitExtension extends JunitExtensionBase
        implements BeforeAllCallback,
                   AfterAllCallback,
                   AfterEachCallback,
                   ParameterResolver {

    private final Map<String, URI> uris = new ConcurrentHashMap<>();
    private final List<ServerJunitExtension> extensions;

    private WebServer server;
    private PinningRecorder pinningRecorder;

    HelidonServerJunitExtension() {
        this.extensions = HelidonServiceLoader.create(ServiceLoader.load(ServerJunitExtension.class)).asList();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        super.beforeAll(context);

        run(context, () -> {
            if (System.getProperty("helidon.config.profile") == null
                    && System.getProperty("config.profile") == null) {
                System.setProperty("helidon.config.profile", "test");
            }
            TestConfigSource testConfigSource = new TestConfigSource();
            Services.add(ConfigSource.class, 10000D, testConfigSource);

            Class<?> testClass = context.getRequiredTestClass();
            super.testClass(testClass);
            ServerTest testAnnot = testClass.getAnnotation(ServerTest.class);
            if (testAnnot == null) {
                throw new IllegalStateException("Invalid test class for this extension: " + testClass);
            }

            if (testAnnot.pinningDetection()) {
                pinningRecorder = PinningRecorder.create();
                pinningRecorder.record(Duration.ofMillis(testAnnot.pinningThreshold()));
            }

            WebServerConfig.Builder builder = WebServer.builder();

            builder.config(Services.get(Config.class).get("server"));
            setupWebServerFromRegistry(builder);
            builder.host("localhost");

            extensions.forEach(it -> it.beforeAll(context));
            extensions.forEach(it -> it.updateServerBuilder(builder));

            // port will be random
            builder.port(0)
                    .shutdownHook(false);

            setupServer(builder);
            addRouting(builder);

            server = builder
                    .serverContext(staticContext(context).orElseThrow()) // created above when we call super.beforeAll
                    .build()
                    .start();
            if (server.hasTls()) {
                uris.put(DEFAULT_SOCKET_NAME, URI.create("https://localhost:" + server.port() + "/"));
            } else {
                uris.put(DEFAULT_SOCKET_NAME, URI.create("http://localhost:" + server.port() + "/"));
            }

            testConfigSource.set("test.server.port", String.valueOf(server.port()));
        });
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        run(extensionContext, () -> {
            extensions.forEach(it -> it.afterAll(extensionContext));

            if (server != null) {
                server.stop();
            }

            super.afterAll(extensionContext);

            if (pinningRecorder != null) {
                pinningRecorder.close();
                pinningRecorder = null;
            }
        });
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        runChecked(extensionContext, () -> extensions.forEach(it -> it.afterEach(extensionContext)));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        return supplyChecked(extensionContext, () -> {
            Class<?> paramType = parameterContext.getParameter().getType();
            if (paramType.equals(WebServer.class)) {
                return true;
            }
            if (paramType.equals(URI.class)) {
                return true;
            }

            for (ServerJunitExtension extension : extensions) {
                if (extension.supportsParameter(parameterContext, extensionContext)) {
                    return true;
                }
            }

            Context context;
            if (server == null) {
                context = Contexts.context().orElseGet(Contexts::globalContext);
            } else {
                context = server.context();
            }
            if (context.get(paramType).isPresent()) {
                return true;
            }
            return super.supportsParameter(parameterContext, extensionContext);
        });
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        return supplyChecked(extensionContext, () -> {
            Class<?> paramType = parameterContext.getParameter().getType();
            if (paramType.equals(WebServer.class)) {
                return server;
            }
            if (paramType.equals(URI.class)) {
                return uri(parameterContext.getDeclaringExecutable(), Junit5Util.socketName(parameterContext.getParameter()));
            }

            for (ServerJunitExtension extension : extensions) {
                if (extension.supportsParameter(parameterContext, extensionContext)) {
                    return extension.resolveParameter(parameterContext, extensionContext, paramType, server);
                }
            }

            Context context;
            if (server == null) {
                context = Contexts.context().orElseGet(Contexts::globalContext);
            } else {
                context = server.context();
            }

            var fromContext = context.get(paramType);

            if (fromContext.isPresent()) {
                return fromContext;
            }

            return super.resolveParameter(parameterContext, extensionContext);
        });
    }

    private static void setupWebServerFromRegistry(WebServerConfig.Builder serverBuilder) {
        Object o = GlobalServiceRegistry.registry()
                .get(WebServerService__ServiceDescriptor.INSTANCE)
                .orElseThrow(() -> {
                    return new IllegalStateException("Could not discover WebServerService in service registry, both "
                                                             + "'helidon-service-registry' and `helidon-webserver` must be on "
                                                             + "classpath.");
                });
        // the service is package local
        Class<?> clazz = o.getClass();
        try {
            Method method = clazz.getDeclaredMethod("updateServerBuilder", WebServerConfig.BuilderBase.class);
            method.setAccessible(true);
            method.invoke(o, serverBuilder);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to get service registry specific method on WebServerService", e);
        }
    }

    private URI uri(Executable declaringExecutable, String socketName) {
        URI uri = uris.computeIfAbsent(socketName, it -> {
            int port = server.port(it);
            if (port == -1) {
                return null;
            }
            if (server.hasTls(it)) {
                return URI.create("https://localhost:" + port + "/");
            }
            return URI.create("http://localhost:" + port + "/");
        });

        if (uri == null) {
            throw new IllegalStateException(declaringExecutable + " expects injection of URI parameter for socket named "
                                                    + socketName
                                                    + ", which is not available on the running webserver");
        }
        return uri;
    }

    private void setupServer(WebServerConfig.Builder builder) {
        withStaticMethods(testClass(), SetUpServer.class, (setUpServer, method) -> {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpServer.class.getSimpleName()
                                                           + " does not have exactly one parameter (WebServerConfig.Builder)");
            }
            if (!parameterTypes[0].equals(WebServerConfig.Builder.class)) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpServer.class.getSimpleName()
                                                           + " does not have exactly one parameter (WebServerConfig.Builder)");
            }
            try {
                method.setAccessible(true);
                method.invoke(null, builder);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Could not invoke method " + method, e);
            }
        });
    }

    private void addRouting(WebServerConfig.Builder builder) {
        Map<String, ListenerConfig.Builder> listenerConfigs = new HashMap<>();
        Map<String, Router.Builder> routerBuilders = new HashMap<>();

        listenerConfigs.put(DEFAULT_SOCKET_NAME, ListenerConfig.builder().from(builder));

        withStaticMethods(testClass(), SetUpRoute.class, (setUpRoute, method) -> {
            // validate parameters
            String socketName = setUpRoute.value();

            SetUpRouteHandler methodConsumer = createRoutingMethodCall(method);

            ListenerConfig.Builder socketBuilder = listenerConfigs.computeIfAbsent(socketName, it -> ListenerConfig.builder());
            Router.RouterBuilder<?> route = routerBuilders.computeIfAbsent(socketName, it -> Router.builder());

            extensions.forEach(it -> it.updateListenerBuilder(socketName,
                                                              socketBuilder,
                                                              route));

            methodConsumer.handle(socketName, builder, socketBuilder, route);
        });

        routerBuilders.forEach((socketName, routerBuilder) -> {
            if (DEFAULT_SOCKET_NAME.equals(socketName)) {
                builder.addRoutings(routerBuilder.routings());
            } else {
                listenerConfigs.computeIfAbsent(socketName, it -> ListenerConfig.builder())
                        .addRoutings(routerBuilder.routings());
            }
        });

        listenerConfigs.forEach((socketName, listenerBuilder) -> {
            if (DEFAULT_SOCKET_NAME.equals(socketName)) {
                builder.from(listenerBuilder);
            } else {
                ListenerConfig listenerConfig = builder.sockets().get(socketName);
                if (listenerConfig == null) {
                    builder.putSocket(socketName, listenerBuilder.build());
                } else {
                    builder.putSocket(socketName, ListenerConfig.builder(listenerConfig).from(listenerBuilder).build());
                }
            }
        });
    }

    private SetUpRouteHandler createRoutingMethodCall(Method method) {
        // @SetUpRoute may have parameters handled by different extensions
        List<ServerJunitExtension.ParamHandler> handlers = new ArrayList<>();

        Parameter[] parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            Class<?> paramType = parameter.getType();

            // for each parameter, resolve a parameter handler
            boolean found = false;
            for (ServerJunitExtension extension : extensions) {
                Optional<? extends ServerJunitExtension.ParamHandler> paramHandler =
                        extension.setUpRouteParamHandler(paramType);
                if (paramHandler.isPresent()) {
                    // we care about the extension with the highest priority only
                    handlers.add(paramHandler.get());
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Method " + method + " has a parameter " + paramType + " that is "
                                                           + "not supported by any available testing extension");
            }
        }
        // now we have the same number of parameter handlers as we have parameters
        return (socketName, serverBuilder, listenerBuilder, routerBuilder) -> {
            Object[] values = new Object[handlers.size()];

            for (int i = 0; i < handlers.size(); i++) {
                ServerJunitExtension.ParamHandler<?> handler = handlers.get(i);
                values[i] = handler.get(socketName, serverBuilder, listenerBuilder, routerBuilder);
            }

            try {
                method.setAccessible(true);
                method.invoke(null, values);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Cannot invoke router/socket method", e);
            }

            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                ServerJunitExtension.ParamHandler handler = handlers.get(i);
                handler.handle(socketName, serverBuilder, listenerBuilder, routerBuilder, value);
            }
        };
    }

    private interface SetUpRouteHandler {
        void handle(String socketName,
                    WebServerConfig.Builder serverBuilder,
                    ListenerConfig.Builder listenerBuilder,
                    Router.RouterBuilder<?> routerBuilder);
    }

    private static class TestConfigSource implements ConfigSource, LazyConfigSource {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public Optional<ConfigNode> node(String key) {
            return Optional.ofNullable(values.get(key))
                    .map(ConfigNode.ValueNode::create);
        }

        private void set(String key, String value) {
            values.put(key, value);
        }
    }
}
