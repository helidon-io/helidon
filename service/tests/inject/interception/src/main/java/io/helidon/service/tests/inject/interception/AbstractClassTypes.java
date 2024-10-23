/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject.interception;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Interception;
import io.helidon.service.inject.api.InterceptionContext;
import io.helidon.service.registry.Service;

/**
 * An example that illustrates usages of {@link io.helidon.service.inject.api.Interception.Interceptor}.
 */
class AbstractClassTypes {

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
    @Injection.Singleton
    @Injection.NamedByType(Traced.class)
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
     * A service that extends an abstract class contract with an intercepted method.
     */
    @Injection.Singleton
    static class MyAbstractClassContractImpl extends MyAbstractClassContract {

        @Override
        public String sayHello(String name) {
            return "Hello %s!".formatted(name);
        }
    }
}
