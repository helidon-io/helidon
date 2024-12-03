/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.microprofile.testing.junit5.HelidonTestInfo.ClassInfo;
import io.helidon.microprofile.testing.junit5.HelidonTestInfo.MethodInfo;

import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
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
 *     <li>{@link DisableDiscovery} disables CDI discovery</li>
 *     <li>{@link AddBean} add CDI beans</li>
 *     <li>{@link AddExtension} add CDI extension</li>
 *     <li>{@link AddJaxRs} add JAX-RS (Jersey)</li>
 * </ul>
 * <p>
 * The configuration can be customized with the following annotations:
 * <ul>
 *     <li>{@link Configuration} global setting for MicroProfile configuration</li>
 *     <li>{@link AddConfig} declarative key/value pair configuration</li>
 *     <li>{@link AddConfigBlock} declarative fragment configuration</li>
 *     <li>{@link AddConfigSource} programmatic configuration</li>
 * </ul>
 * <p>
 * See also {@link Socket}, a CDI qualifier to inject JAX-RS client or URI.
 * <p>
 * The container is created per test class by default, unless
 * {@link HelidonTest#resetPerTest()} is {@code true}, in
 * which case the container is created per test method.
 * <p>
 * The container and the configuration can be customized per method regardless of the value of
 * {@link HelidonTest#resetPerTest()}. The container will be reset accordingly.
 * <p>
 * It is not recommended to provide a {@code beans.xml} along the test classes, as it would combine beans from all tests.
 * Instead, you should use {@link AddBean} to specify the beans per test or method.
 *
 * @see HelidonTest
 */
public class HelidonJunitExtension implements BeforeEachCallback,
                                              TestInstanceFactory,
                                              InvocationInterceptor,
                                              ParameterResolver {

    private static final Namespace NAMESPACE = Namespace.create(HelidonJunitExtension.class);

    private final Map<Class<?>, ClassInfo> classInfos = new ConcurrentHashMap<>();
    private final Map<Method, MethodInfo> methodInfos = new ConcurrentHashMap<>();

    @Override
    public Object createTestInstance(TestInstanceFactoryContext fc, ExtensionContext context) {
        // Use a proxy to start the container after the test instance creation
        // The container is started lazily when invoking a method
        // or when resolving parameters
        return ProxyHelper.proxyDelegate(context.getRequiredTestClass(), (testClass, testMethod) -> {
            // class context store specific to the intercepted method
            return container(context, testMethod).resolveInstance(testClass);
        });
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation,
                                          ReflectiveInvocationContext<Method> ic,
                                          ExtensionContext context) throws Throwable {

        intercept(invocation, ic, context);
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation,
                                         ReflectiveInvocationContext<Method> ic,
                                         ExtensionContext context) throws Throwable {

        intercept(invocation, ic, context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Method testMethod = context.getRequiredTestMethod();
        Class<?> testClass = context.getRequiredTestClass();

        ClassInfo classInfo = classInfos.computeIfAbsent(testClass, ClassInfo::new);
        MethodInfo methodInfo = methodInfos.computeIfAbsent(testMethod, e -> new MethodInfo(e, classInfo));

        ExtensionContext classContext = classContext(context);
        Store classStore = classContext.getStore(NAMESPACE);
        HelidonTestContainer container = classStore.get("container", HelidonTestContainer.class);

        if (context.getExecutionMode() == ExecutionMode.SAME_THREAD
            && container != null && !container.closed()
            && methodInfo.requiresReset()) {

            // close the "class container" only for sequential executions
            // parallel & requireReset use multiple containers
            container.close();
        }

        if (container == null || container.closed()) {
            Store methodStore = context.getStore(NAMESPACE);
            Lifecycle lifecycle = context.getTestInstanceLifecycle().orElse(PER_METHOD);
            HelidonTestScope scope;
            if (lifecycle == Lifecycle.PER_CLASS) {
                scope = new HelidonTestScope.PerContainer();
            } else {
                scope = new HelidonTestScope.PerThread();
                // put the scope in the method context store to auto-close
                methodStore.put("scope", (CloseableResource) scope::close);
            }
            if (methodInfo.requiresReset()) {
                // put in the method store to auto-close
                container = new CloseableContainer(methodInfo, scope);
                methodStore.put("container", container);
            } else {
                // put the "class container" in the class context store
                // to re-use between methods
                container = classStore.getOrComputeIfAbsent("container",
                        k -> new CloseableContainer(classInfo, scope), CloseableContainer.class);
            }
        }
        // proxy handler uses class context
        // hence we use a class context store specific to the test method
        store(classContext, testMethod).put("container", container);
    }

    @Override
    public boolean supportsParameter(ParameterContext pc, ExtensionContext context)
            throws ParameterResolutionException {

        HelidonTestContainer container = container(context, context.getRequiredTestMethod());
        return !container.initFailed() && container.isSupported(pc.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext pc, ExtensionContext context)
            throws ParameterResolutionException {

        HelidonTestContainer container = container(context, context.getRequiredTestMethod());
        return container.initFailed() ? null : container.resolveInstance(pc.getParameter().getType());
    }

    private void intercept(Invocation<Void> invocation,
                           ReflectiveInvocationContext<Method> ic,
                           ExtensionContext context) throws Throwable {

        HelidonTestContainer container = container(context, context.getRequiredTestMethod());
        if (container.initFailed()) {
            invocation.skip();
        } else {
            // proxy handler uses class context
            // hence we use a class context store specific to the test method
            ExtensionContext classContext = classContext(context);
            store(classContext, ic.getExecutable()).put("container", container);
            invocation.proceed();
        }
    }

    private static Store store(ExtensionContext context, Executable executable) {
        return context.getStore(NAMESPACE.append(executable.getName()));
    }

    private static HelidonTestContainer container(ExtensionContext context, Method method) {
        HelidonTestContainer container = store(context, method).get("container", HelidonTestContainer.class);
        if (container == null) {
            throw new IllegalStateException("Container not set");
        }
        return container;
    }

    private static ExtensionContext classContext(ExtensionContext context) {
        while (!context.getElement().map(Class.class::isInstance).orElse(false)) {
            context = context.getParent().orElseThrow();
        }
        return context;
    }

    private static final class CloseableContainer extends HelidonTestContainer
            implements CloseableResource {

        CloseableContainer(HelidonTestInfo testInfo, HelidonTestScope testContext) {
            super(testInfo, testContext);
        }

        @Override
        public void close() {
            super.close();
        }
    }
}
