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
package io.helidon.docs.se.inject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;

class InterceptorDelegateExample {

    // tag::snippet_1[]
    @Interception.Intercepted
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @interface Traced {
    }

    @Service.Singleton
    @Service.NamedByType(Traced.class)
    class MyServiceInterceptor implements Interception.Interceptor {

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            //Do something
            return chain.proceed(args);
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Service.Singleton
    class MyServiceProvider implements Supplier<MyService> {
        @Override
        public MyService get() {
            return new MyService();
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @Service.Contract
    @Interception.Delegate
    class MyService {

        @Traced
        String sayHello(String name) {
            return "Hello %s!".formatted(name);
        }

    }
    // end::snippet_3[]



    // tag::snippet_4[]
    /**
     * Assume this is the class we have no control over.
     */
    class SomeExternalClass {
        String sayHello(String name) {
            return "Hello %s!".formatted(name);
        }
    }

    @Service.Singleton
    @Interception.ExternalDelegate(SomeExternalClass.class)
    class SomeExternalClassProvider implements Supplier<SomeExternalClass> {
        @Override
        public SomeExternalClass get() {
            return new SomeExternalClass();
        }
    }
    // end::snippet_4[]

}
