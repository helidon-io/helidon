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

class InterceptorExample {

    // tag::snippet_1[]
    /**
     * An annotation to mark methods to be intercepted.
     */
    @Interception.Intercepted
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @interface Traced {
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Service.Singleton
    @Service.NamedByType(Traced.class) //<1>
    static class MyServiceInterceptor implements Interception.Interceptor {
        static final List<String> INVOKED = new ArrayList<>();

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            INVOKED.add("%s.%s: %s".formatted(
                    ctx.serviceInfo().serviceType().declaredName(),
                    ctx.elementInfo().elementName(),
                    Arrays.asList(args)));
            return chain.proceed(args); //<2>
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @Service.Singleton
    static class MyServiceProvider implements Supplier<MyService> {
        @Override
        public MyService get() {
            return new MyService();
        }
    }

    @Service.Contract
    @Interception.Delegate
    static class MyService {

        @Traced
        String sayHello(String name) {
            return "Hello %s!".formatted(name);
        }

    }
    // end::snippet_3[]

}
