/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.testing.junit5;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.microprofile.testing.HelidonTestInfo.ClassInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.MethodInfo;
import io.helidon.microprofile.testing.HelidonTestScope;
import io.helidon.microprofile.testing.Instrumented;
import io.helidon.testing.junit5.TestJunitExtension;

import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static io.helidon.microprofile.testing.HelidonTestInfo.classInfo;
import static io.helidon.microprofile.testing.HelidonTestInfo.methodInfo;
import static io.helidon.microprofile.testing.Instrumented.instrument;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD;

/**
 * A JUnit5 extension that integrates CDI with JUnit to support Helidon MP.
 * <p>
 * This extension starts a CDI container and adds the test class as a bean with support for injection. The test class uses
 * a CDI scope that follows the test lifecycle as defined by {@link org.junit.jupiter.api.TestInstance TestInstance}.
 * <p>
 * The container is started lazily during test execution to ensure that it is started after all other extensions.
 * <p>
 * The container can be customized with the following annotations:
 * <ul>
 *     <li>{@link HelidonTest#resetPerTest()} force a new CDI container per test</li>
 *     <li>{@link io.helidon.microprofile.testing.DisableDiscovery} disables CDI discovery</li>
 *     <li>{@link io.helidon.microprofile.testing.AddBean} add CDI beans</li>
 *     <li>{@link io.helidon.microprofile.testing.AddExtension} add CDI extension</li>
 *     <li>{@link io.helidon.microprofile.testing.AddJaxRs} add JAX-RS (Jersey)</li>
 * </ul>
 * <p>
 * The configuration can be customized with the following annotations:
 * <ul>
 *     <li>{@link io.helidon.microprofile.testing.Configuration} global setting for MicroProfile configuration</li>
 *     <li>{@link io.helidon.microprofile.testing.AddConfig} declarative key/value pair configuration</li>
 *     <li>{@link io.helidon.microprofile.testing.AddConfigBlock} declarative fragment configuration</li>
 *     <li>{@link io.helidon.microprofile.testing.AddConfigSource} programmatic configuration</li>
 * </ul>
 * <p>
 * See also {@link io.helidon.microprofile.testing.Socket}, a CDI qualifier to inject JAX-RS client or URI.
 * <p>
 * The container is created per test class by default, unless
 * {@link HelidonTest#resetPerTest()} is {@code true}, in
 * which case the container is created per test method.
 * <p>
 * The container and the configuration can be customized per method regardless of the value of
 * {@link HelidonTest#resetPerTest()}. The container will be reset accordingly.
 * <p>
 * It is not recommended to provide a {@code beans.xml} along the test classes, as it would combine beans from all tests.
 * Instead, you should use {@link io.helidon.microprofile.testing.AddBean} to specify the beans per test or method.
 *
 * @see HelidonTest
 */
public class HelidonJunitExtension extends TestJunitExtension
        implements BeforeEachCallback,
                   TestInstanceFactory,
                   InvocationInterceptor,
                   ParameterResolver {

    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public Object createTestInstance(TestInstanceFactoryContext fc, ExtensionContext ctx) {
        initStaticContext(ctx);
        return supplyChecked(ctx, () -> {
            // Instrument the test class
            // Use a proxy to start the container lazily
            Class<?> testClass = instrument(ctx.getRequiredTestClass(), List.of(), List.of(),
                    (type, method) -> {
                        // class context store specific to the intercepted method
                        Store store = store(ctx, method);
                        return requiredContainer(store).resolveInstance(type);
                    });
            return Instrumented.allocateInstance(testClass);
        });
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation,
                                          ReflectiveInvocationContext<Method> ic,
                                          ExtensionContext ctx) throws Throwable {

        invoke(invocation, ic, ctx);
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation,
                                         ReflectiveInvocationContext<Method> ic,
                                         ExtensionContext ctx) throws Throwable {

        invoke(invocation, ic, ctx);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        run(context, () -> {
            Method testMethod = context.getRequiredTestMethod();
            Class<?> testClass = context.getRequiredTestClass();

            ClassInfo classInfo = classInfo(testClass, HelidonTestDescriptorImpl::new);
            MethodInfo methodInfo = methodInfo(testMethod, classInfo, HelidonTestDescriptorImpl::new);

            ExtensionContext classContext = classContext(context);
            Store classStore = store(classContext);
            HelidonTestContainerImpl container = container(classStore);

            if (context.getExecutionMode() == ExecutionMode.SAME_THREAD
                && container != null && !container.closed()
                && methodInfo.requiresReset()) {

                // close the "class container" only for sequential executions
                // parallel & requireReset use multiple containers
                container.close();
            }

            if (container == null || container.closed()) {
                Store methodStore = store(context);
                Lifecycle lifecycle = context.getTestInstanceLifecycle().orElse(PER_METHOD);
                HelidonTestScope scope;
                if (lifecycle == Lifecycle.PER_CLASS) {
                    scope = HelidonTestScope.ofContainer();
                } else {
                    scope = HelidonTestScope.ofThread();
                    // put the scope in the method context store to auto-close
                    methodStore.put("scope", (CloseableResource) scope::close);
                }
                if (methodInfo.requiresReset()) {
                    // put in the method store to auto-close
                    container = new HelidonTestContainerImpl(methodInfo, scope);
                    methodStore.put("container", container);
                } else {
                    // put the "class container" in the class context store
                    // to re-use between methods
                    lock.lock();
                    try {
                        container = container(classStore);
                        if (container == null || container.closed()) {
                            container = new HelidonTestContainerImpl(classInfo, scope);
                            classStore.put("container", container);
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            }
            // proxy handler uses class context
            // hence we use a class context store specific to the test method
            store(classContext, testMethod).put("container", container);
        });
    }

    @Override
    public boolean supportsParameter(ParameterContext pc, ExtensionContext ctx)
            throws ParameterResolutionException {

        return supplyChecked(ctx, () -> {
            Store store = store(ctx, ctx.getRequiredTestMethod());
            HelidonTestContainerImpl container = requiredContainer(store);
            return !container.initFailed() && container.isSupported(pc.getParameter().getType());
        });
    }

    @Override
    public Object resolveParameter(ParameterContext pc, ExtensionContext ctx)
            throws ParameterResolutionException {

        return supplyChecked(ctx, () -> {
            Store store = store(ctx, ctx.getRequiredTestMethod());
            HelidonTestContainerImpl container = requiredContainer(store);
            return container.initFailed() ? null : container.resolveInstance(pc.getParameter().getType());
        });
    }

    private void invoke(Invocation<Void> invocation,
                        ReflectiveInvocationContext<Method> ic,
                        ExtensionContext context) throws Throwable {

        runChecked(context, () -> {
            Store methodStore = store(context, context.getRequiredTestMethod());
            HelidonTestContainerImpl container = requiredContainer(methodStore);
            if (container.initFailed()) {
                invocation.skip();
            } else {
                // proxy handler uses class context
                // hence we use a class context store specific to the test method
                ExtensionContext classContext = classContext(context);
                Store store = store(classContext, ic.getExecutable());
                store.put("container", container);
                invocation.proceed();
            }
        });
    }

    private static HelidonTestContainerImpl container(Store store) {
        return storeLookup(store, "container", HelidonTestContainerImpl.class)
                .orElse(null);
    }

    private static HelidonTestContainerImpl requiredContainer(Store store) {
        return storeLookup(store, "container", HelidonTestContainerImpl.class)
                .orElseThrow(() -> new IllegalStateException("Container not set"));
    }

    private static ExtensionContext classContext(ExtensionContext context) {
        ExtensionContext c = context;
        while (!c.getElement().map(Class.class::isInstance).orElse(false)) {
            c = c.getParent().orElseThrow();
        }
        return c;
    }
}
