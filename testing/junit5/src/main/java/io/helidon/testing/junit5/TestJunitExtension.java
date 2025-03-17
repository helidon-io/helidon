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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
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
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.TestException;
import io.helidon.testing.TestRegistry;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.platform.commons.support.ModifierSupport;

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

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(TestJunitExtension.class);

    static {
        LogConfig.initClass();
    }

    /**
     * Default constructor with no side effects.
     */
    protected TestJunitExtension() {
    }

    @Override
    public void beforeAll(ExtensionContext ctx) {
        var store = store(ctx, ctx.getRequiredTestClass());
        initStaticContext(store, ctx);
        run(ctx, LogConfig::configureRuntime);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        run(context, () -> afterShutdownMethods(context.getRequiredTestClass()));
    }

    @Override
    public boolean supportsParameter(ParameterContext pc, ExtensionContext ctx)
            throws ParameterResolutionException {

        return supplyChecked(ctx, () -> {
            var paramType = pc.getParameter().getType();
            var genericParamType = GenericType.create(pc.getParameter().getParameterizedType());
            if (!genericParamType.isClass()) {
                return false;
            }
            return supportedType(GlobalServiceRegistry.registry(), paramType);
        });
    }

    @Override
    public Object resolveParameter(ParameterContext pc, ExtensionContext ctx)
            throws ParameterResolutionException {

        return supplyChecked(ctx, () -> {
            var paramType = pc.getParameter().getType();
            var registry = GlobalServiceRegistry.registry();
            if (supportedType(registry, paramType)) {
                return registry.get(paramType);
            }
            throw new ParameterResolutionException("Failed to resolve parameter of type "
                                                   + paramType.getName());
        });
    }

    @Override
    public <T> T interceptTestClassConstructor(Invocation<T> invocation,
                                               ReflectiveInvocationContext<Constructor<T>> ic,
                                               ExtensionContext ctx) throws Throwable {
        return invoke(ctx, invocation);
    }

    @Override
    public void interceptBeforeAllMethod(Invocation<Void> invocation,
                                         ReflectiveInvocationContext<Method> ic,
                                         ExtensionContext ctx) throws Throwable {
        invoke(ctx, invocation);
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation,
                                          ReflectiveInvocationContext<Method> ic,
                                          ExtensionContext ctx) throws Throwable {
        invoke(ctx, invocation);
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> ic,
                                    ExtensionContext ctx) throws Throwable {
        invoke(ctx, invocation);
    }

    @Override
    public <T> T interceptTestFactoryMethod(Invocation<T> invocation,
                                            ReflectiveInvocationContext<Method> ic,
                                            ExtensionContext ctx) throws Throwable {
        return invoke(ctx, invocation);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> ic,
                                            ExtensionContext ctx) throws Throwable {
        invoke(ctx, invocation);
    }

    @Override
    public void interceptDynamicTest(Invocation<Void> invocation,
                                     DynamicTestInvocationContext ic,
                                     ExtensionContext ctx) throws Throwable {
        invoke(ctx, invocation);
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation,
                                         ReflectiveInvocationContext<Method> ic,
                                         ExtensionContext ctx) throws Throwable {
        invoke(ctx, invocation);
    }

    @Override
    public void interceptAfterAllMethod(Invocation<Void> invocation,
                                        ReflectiveInvocationContext<Method> ic,
                                        ExtensionContext ctx) throws Throwable {
        invoke(ctx, invocation);
    }

    /**
     * Initialize the static context to be used for all actions this extension invokes, and to store the global instances.
     * This extension creates a unit test context by default for each test class.
     *
     * @param ctx JUnit extension context
     */
    protected void initStaticContext(ExtensionContext ctx) {
        initStaticContext(store(ctx, ctx.getRequiredTestClass()), ctx);
    }

    /**
     * Initialize the static context to be used for all actions this extension invokes, and to store the global instances.
     * This extension creates a unit test context by default for each test class.
     *
     * @param store JUnit extension store
     * @param ctx   JUnit extension context
     */
    protected void initStaticContext(ExtensionContext.Store store, ExtensionContext ctx) {
        store.getOrComputeIfAbsent(Context.class, c -> {
            var testClass = ctx.getRequiredTestClass();
            var context = Context.builder()
                    .id("test-" + testClass.getName() + "-" + System.identityHashCode(testClass))
                    .build();

            // self-register, so this context is used even if the current context is some child of it
            context.register("helidon-registry-static-context", context);

            // supply registry
            context.supply("helidon-registry", ServiceRegistry.class, () -> {
                var manager = ServiceRegistryManager.create();
                var registry = manager.registry();
                store.put(ServiceRegistryManager.class, (CloseableResource) manager::shutdown);
                store.put(ServiceRegistry.class, registry);
                return registry;
            });
            return context;
        });
    }

    /**
     * Get an object from the given store.
     *
     * @param store store
     * @param key   key
     * @param type  type
     * @param <T>   object type
     * @return optional
     */
    protected static <T> Optional<T> storeLookup(ExtensionContext.Store store, Object key, Class<T> type) {
        return Optional.ofNullable(store.get(key, type));
    }

    /**
     * The current "static" context (if already available) that the test actions will be executed in.
     *
     * @param ctx JUnit extension context
     * @return context used by this extension
     */
    protected Optional<Context> staticContext(ExtensionContext ctx) {
        return storeLookup(store(ctx, ctx.getRequiredTestClass()), Context.class, Context.class);
    }

    /**
     * Get a JUnit extension store.
     *
     * @param ctx        JUnit extension context
     * @param qualifiers qualifiers
     * @return JUnit extension store
     */
    protected static ExtensionContext.Store store(ExtensionContext ctx, AnnotatedElement... qualifiers) {
        ExtensionContext.Namespace ns;
        if (qualifiers.length > 0) {
            ns = NAMESPACE.append(Arrays.stream(qualifiers)
                    .map(e -> switch (e) {
                        case Class<?> c -> c.getName();
                        case Method m -> m.getName();
                        default -> throw new IllegalArgumentException("Unsupported element: " + e);
                    })
                    .toArray());
        } else {
            ns = NAMESPACE;
        }
        return ctx.getStore(ns);
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
        return Contexts.runInContext(staticContext(ctx).orElseThrow(), supplier::get);
    }

    /**
     * Call a supplier that can throw {@link Throwable} within context.
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

        T response = Contexts.runInContext(staticContext(ctx).orElseThrow(), () -> {
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
        var throwable = thrown.get();
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
        Contexts.runInContext(staticContext(ctx).orElseThrow(), runnable);
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

        Contexts.runInContext(staticContext(ctx).orElseThrow(), () -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                thrown.set(e);
            }
        });

        if (thrown.get() == null) {
            return;
        }
        var throwable = thrown.get();
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

        T response = Contexts.runInContext(staticContext(ctx).orElseThrow(), () -> {
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
            var annotation = declaredMethod.getAnnotation(TestRegistry.AfterShutdown.class);
            if (annotation != null) {
                if (!ModifierSupport.isStatic(declaredMethod)) {
                    throw new TestException("Cannot invoke @TestRegistry.AfterShutdown annotated method "
                                                    + declaredMethod.getName() + ", as it is not static");
                }

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

    private boolean supportedType(ServiceRegistry registry, Class<?> paramType) {
        if (ServiceRegistry.class.isAssignableFrom(paramType)) {
            return true;
        }
        // we do not want to get the instance here (yet)
        return !registry.allServices(paramType).isEmpty();
    }
}
