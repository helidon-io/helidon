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
