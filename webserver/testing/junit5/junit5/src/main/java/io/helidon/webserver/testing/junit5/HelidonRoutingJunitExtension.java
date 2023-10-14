/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.GlobalConfig;
import io.helidon.common.context.Contexts;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.testing.junit5.spi.DirectJunitExtension;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import static io.helidon.webserver.testing.junit5.Junit5Util.withStaticMethods;

/**
 * JUnit5 extension to support Helidon WebServer in tests.
 */
class HelidonRoutingJunitExtension extends JunitExtensionBase
        implements BeforeAllCallback,
                   AfterAllCallback,
                   InvocationInterceptor,
                   BeforeEachCallback,
                   AfterEachCallback,
                   ParameterResolver {

    private final List<DirectJunitExtension> extensions;
    private WebServerConfig serverConfig;

    HelidonRoutingJunitExtension() {
        this.extensions = HelidonServiceLoader.create(ServiceLoader.load(DirectJunitExtension.class)).asList();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        LogConfig.configureRuntime();

        Class<?> testClass = context.getRequiredTestClass();
        super.testClass(testClass);
        RoutingTest testAnnot = testClass.getAnnotation(RoutingTest.class);
        if (testAnnot == null) {
            throw new IllegalStateException("Invalid test class for this extension: " + testClass + ", missing "
                                                    + RoutingTest.class.getName() + " annotation");
        }

        WebServerConfig.Builder builder = WebServer.builder()
                .config(GlobalConfig.config().get("server"))
                .host("localhost");

        extensions.forEach(it -> it.beforeAll(context));

        setupServer(builder);
        serverConfig = builder.buildPrototype();

        initRoutings();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        extensions.forEach(it -> it.afterAll(context));
        super.afterAll(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        extensions.forEach(it -> it.beforeAll(context));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        extensions.forEach(it -> it.afterEach(context));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        for (DirectJunitExtension extension : extensions) {
            if (extension.supportsParameter(parameterContext, extensionContext)) {
                return true;
            }
        }

        Class<?> paramType = parameterContext.getParameter().getType();
        return Contexts.globalContext()
                .get(paramType)
                .isPresent();
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        Class<?> paramType = parameterContext.getParameter().getType();

        for (DirectJunitExtension extension : extensions) {
            if (extension.supportsParameter(parameterContext, extensionContext)) {
                return extension.resolveParameter(parameterContext, extensionContext, paramType);
            }
        }

        return Contexts.globalContext()
                .get(paramType)
                .orElseThrow(() -> new ParameterResolutionException("Failed to resolve parameter of type "
                                                                            + paramType.getName()));
    }

    private void initRoutings() {
        List<ServerFeature> features = serverConfig.features();

        Junit5Util.withStaticMethods(testClass(), SetUpRoute.class, (
                (setUpRoute, method) -> {
                    String socketName = setUpRoute.value();
                    SetUpRouteHandler methodConsumer = createRoutingMethodCall(features, method);
                    methodConsumer.handle(socketName);
                }));
    }

    private SetUpRouteHandler createRoutingMethodCall(List<ServerFeature> features, Method method) {

        // @SetUpRoute may have parameters handled by different extensions
        List<DirectJunitExtension.ParamHandler> handlers = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            // for each parameter, resolve parameter handler
            boolean found = false;
            for (DirectJunitExtension extension : extensions) {
                Optional<? extends DirectJunitExtension.ParamHandler> paramHandler =
                        extension.setUpRouteParamHandler(features, parameter.getType());
                if (paramHandler.isPresent()) {
                    // we care about the extension with the highest priority only
                    handlers.add(paramHandler.get());
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Method " + method + " has a parameter " + parameter.getType() + " that is "
                                                           + "not supported by any available testing extension");
            }
        }
        return socketName -> {
            Object[] values = new Object[handlers.size()];

            for (int i = 0; i < handlers.size(); i++) {
                values[i] = handlers.get(i).get(socketName);
            }

            try {
                method.setAccessible(true);
                method.invoke(null, values);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Cannot invoke @SetUpRoute method", e);
            }

            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                DirectJunitExtension.ParamHandler handler = handlers.get(i);
                handler.handle(method, socketName, value);
            }
        };
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

    private interface SetUpRouteHandler {
        void handle(String socketName);
    }
}
