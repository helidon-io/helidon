/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.service.tests.interception;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;

/**
 * An example that illustrates usages of {@link io.helidon.service.registry.Interception.Interceptor}.
 */
class AbstractClassTypes {
    static final String ABSTRACT_DIRECT_METHOD = "io.helidon.service.tests.interception."
            + "AbstractClassTypes.MyAbstractClassContract.sayHelloDirect(java.lang.String)";
    static final String IMPL_DIRECT_METHOD = "io.helidon.service.tests.interception."
            + "AbstractClassTypes.MyAbstractClassContractImpl.sayHelloDirect(java.lang.String)";
    static final String DEFAULT_CONTRACT_METHOD = "io.helidon.service.tests.interception."
            + "AbstractClassTypes.DefaultMethodContract.sayHelloDefault(java.lang.String)";
    static final String FIRST_DEFAULT_METHOD = "io.helidon.service.tests.interception."
            + "AbstractClassTypes.FirstDefaultMethodService.sayHelloDefault(java.lang.String)";
    static final String SECOND_DEFAULT_METHOD = "io.helidon.service.tests.interception."
            + "AbstractClassTypes.SecondDefaultMethodService.sayHelloDefault(java.lang.String)";

    /**
     * An annotation to mark methods to be intercepted.
     */
    @Interception.Intercepted
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @interface Traced {
    }

    /**
     * An abstract class contract with an intercepted method.
     */
    @Service.Contract
    static abstract class MyAbstractClassContract {

        @Traced
        abstract String sayHello(String name);

        @Traced
        String sayHelloDirect(String name) {
            return "Hello %s!".formatted(name);
        }
    }

    /**
     * An interceptor implementation that supports {@link AbstractClassTypes.Traced}.
     */
    @Service.Singleton
    @Service.NamedByType(Traced.class)
    static class MyServiceInterceptor implements Interception.Interceptor {
        static final List<String> INVOKED = new ArrayList<>();

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            INVOKED.add("%s.%s: %s".formatted(
                    ctx.serviceInfo().serviceType().declaredName(),
                    ctx.elementInfo().elementName(),
                    Arrays.asList(args)));
            return chain.proceed(args);
        }
    }

    /**
     * Element interceptor named after the abstract class method. This must not match the subtype service.
     */
    @Service.Singleton
    @Service.Named(ABSTRACT_DIRECT_METHOD)
    static class AbstractDirectMethodInterceptor implements Interception.ElementInterceptor {
        static final List<String> INVOKED = new ArrayList<>();

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            INVOKED.add("%s.%s: %s".formatted(
                    ctx.serviceInfo().serviceType().declaredName(),
                    ctx.elementInfo().elementName(),
                    Arrays.asList(args)));
            return chain.proceed(args);
        }
    }

    /**
     * Element interceptor named after the concrete subtype service method.
     */
    @Service.Singleton
    @Service.Named(IMPL_DIRECT_METHOD)
    static class ImplDirectMethodInterceptor implements Interception.ElementInterceptor {
        static final List<String> INVOKED = new ArrayList<>();

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            INVOKED.add("%s.%s: %s".formatted(
                    ctx.serviceInfo().serviceType().declaredName(),
                    ctx.elementInfo().elementName(),
                    Arrays.asList(args)));
            return chain.proceed(args);
        }
    }

    /**
     * Element interceptor named after the default interface method. This must not match implementing services.
     */
    @Service.Singleton
    @Service.Named(DEFAULT_CONTRACT_METHOD)
    static class DefaultContractMethodInterceptor implements Interception.ElementInterceptor {
        static final List<String> INVOKED = new ArrayList<>();

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            INVOKED.add("%s.%s: %s".formatted(
                    ctx.serviceInfo().serviceType().declaredName(),
                    ctx.elementInfo().elementName(),
                    Arrays.asList(args)));
            return chain.proceed(args);
        }
    }

    /**
     * Element interceptor named after the first default-method service.
     */
    @Service.Singleton
    @Service.Named(FIRST_DEFAULT_METHOD)
    static class FirstDefaultMethodInterceptor implements Interception.ElementInterceptor {
        static final List<String> INVOKED = new ArrayList<>();

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            INVOKED.add("%s.%s: %s".formatted(
                    ctx.serviceInfo().serviceType().declaredName(),
                    ctx.elementInfo().elementName(),
                    Arrays.asList(args)));
            return chain.proceed(args);
        }
    }

    /**
     * Element interceptor named after the second default-method service.
     */
    @Service.Singleton
    @Service.Named(SECOND_DEFAULT_METHOD)
    static class SecondDefaultMethodInterceptor implements Interception.ElementInterceptor {
        static final List<String> INVOKED = new ArrayList<>();

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            INVOKED.add("%s.%s: %s".formatted(
                    ctx.serviceInfo().serviceType().declaredName(),
                    ctx.elementInfo().elementName(),
                    Arrays.asList(args)));
            return chain.proceed(args);
        }
    }

    /**
     * A service that extends an abstract class contract with an intercepted method.
     */
    @Service.Singleton
    static class MyAbstractClassContractImpl extends MyAbstractClassContract {

        @Override
        public String sayHello(String name) {
            return "Hello %s!".formatted(name);
        }
    }

    /**
     * A service contract with an intercepted default method.
     */
    @Service.Contract
    interface DefaultMethodContract {
        @Traced
        default String sayHelloDefault(String name) {
            return "Hello %s!".formatted(name);
        }
    }

    /**
     * First service that inherits the default method.
     */
    @Service.Singleton
    static class FirstDefaultMethodService implements DefaultMethodContract {
    }

    /**
     * Second service that inherits the default method.
     */
    @Service.Singleton
    static class SecondDefaultMethodService implements DefaultMethodContract {
    }
}
