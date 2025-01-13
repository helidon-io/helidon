/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.testing.junit5;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.Functions;
import io.helidon.common.GenericType;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.TestException;
import io.helidon.testing.TestRegistry;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

/**
 * Helidon JUnit extension, added through {@link io.helidon.testing.junit5.Testing.Test}.
 * <p>
 * This extension has the following features:
 * <ul>
 *     <li>Run constructor and every test class method within a custom {@link io.helidon.common.context.Context}</li>
 *     <li>Support configuration annotations to set up configuration before running the tests</li>
 *     <li>Support for injection service registry (if on classpath) to discover configuration</li>
 * </ul>
 */
public class TestJunitExtension implements Extension,
                                           InvocationInterceptor,
                                           BeforeAllCallback,
                                           AfterAllCallback,
                                           ParameterResolver {

    static {
        LogConfig.initClass();
    }

    /**
     * Default constructor with no side effects.
     */
    protected TestJunitExtension() {
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        Context helidonContext = Context.builder()
                .id("test-" + testClass.getName() + "-" + System.identityHashCode(testClass))
                .build();
        // self-register, so this context is used even if the current context is some child of it
        helidonContext.register(GlobalServiceRegistry.STATIC_CONTEXT_CLASSIFIER, helidonContext);

        ExtensionContext.Store store = extensionStore(context);
        store.put(Context.class, helidonContext);

        run(context, () -> {
            LogConfig.configureRuntime();
            createRegistry(store, testClass);
        });
    }

    @Override
    public void afterAll(ExtensionContext context) {
        run(context, () -> afterShutdownMethods(context.getRequiredTestClass()));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();
        if (!GenericType.create(parameterContext.getParameter().getParameterizedType())
                .isClass()) {
            return false;
        }

        return registrySupportedType(extensionContext, paramType);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();

        if (registrySupportedType(extensionContext, paramType)) {
            // at this point in time the registry must be ready
            return registry(extensionContext)
                    .orElseThrow()
                    .get(paramType);
        }

        throw new ParameterResolutionException("Failed to resolve parameter of type "
                                                       + paramType.getName());
    }

    @Override
    public <T> T interceptTestClassConstructor(Invocation<T> invocation,
                                               ReflectiveInvocationContext<Constructor<T>> invocationContext,
                                               ExtensionContext extensionContext) throws Throwable {
        return invoke(extensionContext, invocation);
    }

    @Override
    public void interceptBeforeAllMethod(Invocation<Void> invocation,
                                         ReflectiveInvocationContext<Method> invocationContext,
                                         ExtensionContext extensionContext) throws Throwable {
        invoke(extensionContext, invocation);
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation,
                                          ReflectiveInvocationContext<Method> invocationContext,
                                          ExtensionContext extensionContext) throws Throwable {
        invoke(extensionContext, invocation);
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        invoke(extensionContext, invocation);
    }

    @Override
    public <T> T interceptTestFactoryMethod(Invocation<T> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        return invoke(extensionContext, invocation);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        invoke(extensionContext, invocation);
    }

    @Override
    public void interceptDynamicTest(Invocation<Void> invocation,
                                     DynamicTestInvocationContext invocationContext,
                                     ExtensionContext extensionContext) throws Throwable {
        invoke(extensionContext, invocation);
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation,
                                         ReflectiveInvocationContext<Method> invocationContext,
                                         ExtensionContext extensionContext) throws Throwable {
        invoke(extensionContext, invocation);
    }

    @Override
    public void interceptAfterAllMethod(Invocation<Void> invocation,
                                        ReflectiveInvocationContext<Method> invocationContext,
                                        ExtensionContext extensionContext) throws Throwable {
        invoke(extensionContext, invocation);
    }

    /**
     * Service registry associated with the provided extension contexts
     * (uses {@link #extensionStore(org.junit.jupiter.api.extension.ExtensionContext)}).
     *
     * @param extensionContext extension context
     * @return service registry
     */
    protected Optional<ServiceRegistry> registry(ExtensionContext extensionContext) {
        return Optional.ofNullable(extensionStore(extensionContext)
                                           .get(ServiceRegistry.class, ServiceRegistry.class));
    }

    /**
     * Extension store used by this extension to store context, service registry etc.
     *
     * @param ctx extension context
     * @return extension store
     */
    protected ExtensionContext.Store extensionStore(ExtensionContext ctx) {
        Class<?> testClass = ctx.getRequiredTestClass();
        return ctx.getStore(ExtensionContext.Namespace.create(testClass));
    }

    /**
     * Context to be used for all actions this extension invokes, and to store the global instances.
     * This extension creates a unit test context by default for each test class.
     *
     * @param ctx     JUnit extension context
     * @param context Helidon context to set
     */
    protected void context(ExtensionContext ctx, Context context) {
        // self-register, so this context is used even if the current context is some child of it
        context.register(GlobalServiceRegistry.STATIC_CONTEXT_CLASSIFIER, context);
        extensionStore(ctx)
                .put(Context.class, context);
    }

    /**
     * The current context (if already available) that the test actions will be executed in.
     *
     * @param ctx JUnit extension context
     * @return context used by this extension
     */
    protected Optional<Context> context(ExtensionContext ctx) {
        return Optional.ofNullable(extensionStore(ctx).get(Context.class, Context.class));
    }

    /**
     * Call a supplier within context.
     *
     * @param ctx      JUnit extension context
     * @param supplier callable to invoke
     * @param <T>      type of the result
     * @return result of the callable
     * @throws Throwable in case the call to callable threw an exception
     */
    protected <T> T supply(ExtensionContext ctx, Supplier<T> supplier) throws Throwable {
        return Contexts.runInContext(context(ctx).orElseThrow(), supplier::get);
    }

    /**
     * Call a supplier that can throw {@link java.lang.Throwable} within context.
     *
     * @param ctx      JUnit extension context
     * @param supplier supplier to invoke
     * @param <T>      type of the result
     * @param <E>      type of checked exception that is expected
     * @return result of the callable
     * @throws E in case the call to callable threw an exception
     */
    @SuppressWarnings("unchecked")
    protected <T, E extends Throwable> T supplyChecked(ExtensionContext ctx,
                                                       Functions.CheckedSupplier<T, E> supplier) throws E {
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        T response = Contexts.runInContext(context(ctx).orElseThrow(), () -> {
            try {
                return supplier.get();
            } catch (Throwable e) {
                thrown.set(e);
                return null;
            }
        });
        if (thrown.get() == null) {
            return response;
        }
        Throwable throwable = thrown.get();
        if (throwable instanceof RuntimeException rte) {
            throw rte;
        }
        if (throwable instanceof Error err) {
            throw err;
        }
        throw (E) throwable;
    }

    /**
     * Run a runnable within context.
     *
     * @param ctx      JUnit extension context
     * @param runnable runnable to run
     */
    protected void run(ExtensionContext ctx, Runnable runnable) {
        Contexts.runInContext(context(ctx).orElseThrow(), runnable);
    }

    /**
     * Invoke a runnable that may throw a checked exception.
     *
     * @param ctx      JUnit extension context
     * @param runnable runnable to run
     * @param <E>      type of the exception that can be thrown
     * @throws E in case the runnable threw an exception
     */
    @SuppressWarnings("unchecked")
    protected <E extends Throwable> void runChecked(ExtensionContext ctx, Functions.CheckedRunnable<E> runnable) throws E {
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        Contexts.runInContext(context(ctx).orElseThrow(), () -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                thrown.set(e);
            }
        });

        if (thrown.get() == null) {
            return;
        }
        Throwable throwable = thrown.get();
        if (throwable instanceof RuntimeException rte) {
            throw rte;
        }
        if (throwable instanceof Error err) {
            throw err;
        }
        throw (E) throwable;
    }

    /**
     * Invoke a JUnit invocation within context.
     *
     * @param ctx        JUnit extension context
     * @param invocation invocation to invoke
     * @param <T>        type of the returned value
     * @return result of the invocation
     * @throws Throwable in case the invocation threw an exception
     */
    protected <T> T invoke(ExtensionContext ctx, Invocation<T> invocation) throws Throwable {
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        T response = Contexts.runInContext(context(ctx).orElseThrow(), () -> {
            try {
                return invocation.proceed();
            } catch (Throwable e) {
                thrown.set(e);
                return null;
            }
        });
        if (thrown.get() != null) {
            throw thrown.get();
        }
        return response;
    }

    private void afterShutdownMethods(Class<?> requiredTestClass) {
        for (Method declaredMethod : requiredTestClass.getDeclaredMethods()) {
            TestRegistry.AfterShutdown annotation = declaredMethod.getAnnotation(TestRegistry.AfterShutdown.class);
            if (annotation != null) {
                try {
                    declaredMethod.setAccessible(true);
                    declaredMethod.invoke(null);
                } catch (Exception e) {
                    throw new TestException("Failed to invoke @TestRegistry.AfterShutdown annotated method "
                                                    + declaredMethod.getName(), e);

                }
            }
        }
    }

    private void createRegistry(ExtensionContext.Store store, Class<?> testClass) {
        var registryConfig = ServiceRegistryConfig.builder();
        var manager = ServiceRegistryManager.create(registryConfig.build());
        var registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        store.put(ServiceRegistry.class, registry);
        store.put(ServiceRegistryManager.class, new ClosableRegistryManager(manager));
    }

    private boolean registrySupportedType(ExtensionContext ctx, Class<?> paramType) {
        if (ServiceRegistry.class.isAssignableFrom(paramType)) {
            return true;
        }
        // we do not want to get the instance here (yet)
        return !registry(ctx)
                .map(it -> it.allServices(paramType))
                .map(List::isEmpty)
                .orElse(true);
    }

    private record ClosableRegistryManager(ServiceRegistryManager manager)
            implements ExtensionContext.Store.CloseableResource {

        @Override
        public void close() {
            manager.shutdown();
        }
    }
}
