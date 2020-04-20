/*
 * Copyright (c) 2019,2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.server;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.BeanManager;

import io.helidon.grpc.core.PriorityBag;
import io.helidon.grpc.server.MethodDescriptor;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.microprofile.grpc.core.GrpcInterceptor;
import io.helidon.microprofile.grpc.core.GrpcInterceptorBinding;
import io.helidon.microprofile.grpc.core.GrpcInterceptors;
import io.helidon.microprofile.grpc.core.RpcService;
import io.helidon.microprofile.grpc.core.Unary;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Functional test for server side interceptors using annotations.
 */
@ExtendWith(WeldJunit5Extension.class)
@SuppressWarnings("unchecked")
public class InterceptorsTest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(ServerInterceptorOne.class,
                                                 ServerInterceptorTwo.class,
                                                 ServerInterceptorThree.class,
                                                 ServerInterceptorFour.class,
                                                 InterceptedServiceOne.class,
                                                 InterceptedServiceTwo.class,
                                                 InterceptedServiceThree.class,
                                                 InterceptedServiceFour.class,
                                                 InterceptedServiceFive.class,
                                                 InterceptedServiceSix.class);

    @Test
    public void shouldDiscoverServiceInterceptor() {
        BeanManager beanManager = weld.getBeanManager();
        GrpcServiceBuilder builder = GrpcServiceBuilder.create(InterceptedServiceOne.class, beanManager);
        ServiceDescriptor descriptor = builder.build();

        boolean hasInterceptor = descriptor.interceptors()
                .stream()
                .anyMatch(interceptor -> ServerInterceptorOne.class.isAssignableFrom(interceptor.getClass()));

        assertThat(hasInterceptor, is(true));
        assertThat(sizeOf(descriptor.method("foo").interceptors()), is(0));
    }

    @Test
    public void shouldDiscoverMethodInterceptor() {
        BeanManager beanManager = weld.getBeanManager();
        GrpcServiceBuilder builder = GrpcServiceBuilder.create(InterceptedServiceTwo.class, beanManager);
        ServiceDescriptor descriptor = builder.build();

        assertThat(sizeOf(descriptor.interceptors()), is(0));
        assertThat(sizeOf(descriptor.method("bar").interceptors()), is(0));

        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("foo");
        boolean hasInterceptor = methodDescriptor
                .interceptors()
                .stream()
                .anyMatch(interceptor -> ServerInterceptorTwo.class.isAssignableFrom(interceptor.getClass()));

        assertThat(hasInterceptor, is(true));
    }

    @Test
    public void shouldDiscoverServiceAndMethodInterceptor() {
        BeanManager beanManager = weld.getBeanManager();
        GrpcServiceBuilder builder = GrpcServiceBuilder.create(InterceptedServiceThree.class, beanManager);
        ServiceDescriptor descriptor = builder.build();

        boolean hasServiceInterceptor = descriptor.interceptors()
                .stream()
                .anyMatch(interceptor -> ServerInterceptorOne.class.isAssignableFrom(interceptor.getClass()));

        assertThat(hasServiceInterceptor, is(true));

        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("foo");
        boolean hasMethodInterceptor = methodDescriptor
                .interceptors()
                .stream()
                .anyMatch(interceptor -> ServerInterceptorTwo.class.isAssignableFrom(interceptor.getClass()));

        assertThat(hasMethodInterceptor, is(true));

        assertThat(sizeOf(descriptor.method("bar").interceptors()), is(0));
    }

    @Test
    public void shouldDiscoverServiceInterceptorBasedOnAnnotationMemberValue() {
        BeanManager beanManager = weld.getBeanManager();
        GrpcServiceBuilder builder = GrpcServiceBuilder.create(InterceptedServiceFour.class, beanManager);
        ServiceDescriptor descriptor = builder.build();

        boolean hasInterceptor = descriptor.interceptors()
                .stream()
                .anyMatch(interceptor -> ServerInterceptorFour.class.isAssignableFrom(interceptor.getClass()));

        assertThat(hasInterceptor, is(true));
        assertThat(sizeOf(descriptor.method("foo").interceptors()), is(0));
    }

    @Test
    public void shouldUseSpecificServiceInterceptorBean() {
        BeanManager beanManager = weld.getBeanManager();
        GrpcServiceBuilder builder = GrpcServiceBuilder.create(InterceptedServiceFive.class, beanManager);
        ServiceDescriptor descriptor = builder.build();

        PriorityBag<ServerInterceptor> interceptors = descriptor.interceptors();
        boolean hasInterceptorOne = interceptors
                .stream()
                .anyMatch(interceptor -> ServerInterceptorOne.class.isAssignableFrom(interceptor.getClass()));

        boolean hasInterceptorTwo = interceptors
                .stream()
                .anyMatch(interceptor -> ServerInterceptorTwo.class.isAssignableFrom(interceptor.getClass()));

        assertThat(hasInterceptorOne, is(true));
        assertThat(hasInterceptorTwo, is(true));
        assertThat(sizeOf(descriptor.method("foo").interceptors()), is(0));
    }

    @Test
    public void shouldUseSpecificMethodInterceptorBean() {
        BeanManager beanManager = weld.getBeanManager();
        GrpcServiceBuilder builder = GrpcServiceBuilder.create(InterceptedServiceSix.class, beanManager);
        ServiceDescriptor descriptor = builder.build();

        assertThat(sizeOf(descriptor.interceptors()), is(0));

        PriorityBag<ServerInterceptor> interceptors = descriptor.method("foo").interceptors();
        boolean hasInterceptorOne = interceptors
                .stream()
                .anyMatch(interceptor -> ServerInterceptorOne.class.isAssignableFrom(interceptor.getClass()));

        boolean hasInterceptorTwo = interceptors
                .stream()
                .anyMatch(interceptor -> ServerInterceptorTwo.class.isAssignableFrom(interceptor.getClass()));

        assertThat(hasInterceptorOne, is(true));
        assertThat(hasInterceptorTwo, is(true));
    }

    @Test
    public void shouldUseSpecificNonCdiServiceInterceptor() {
        BeanManager beanManager = weld.getBeanManager();
        GrpcServiceBuilder builder = GrpcServiceBuilder.create(InterceptedServiceSeven.class, beanManager);
        ServiceDescriptor descriptor = builder.build();

        PriorityBag<ServerInterceptor> interceptors = descriptor.interceptors();
        boolean hasInterceptor = interceptors
                .stream()
                .anyMatch(interceptor -> ServerInterceptorFive.class.isAssignableFrom(interceptor.getClass()));

        assertThat(hasInterceptor, is(true));
        assertThat(sizeOf(descriptor.method("foo").interceptors()), is(0));
    }

    @Test
    public void shouldUseSpecificNonCdiMethodInterceptor() {
        BeanManager beanManager = weld.getBeanManager();
        GrpcServiceBuilder builder = GrpcServiceBuilder.create(InterceptedServiceEight.class, beanManager);
        ServiceDescriptor descriptor = builder.build();

        assertThat(sizeOf(descriptor.interceptors()), is(0));

        PriorityBag<ServerInterceptor> interceptors = descriptor.method("foo").interceptors();
        boolean hasInterceptor = interceptors
                .stream()
                .anyMatch(interceptor -> ServerInterceptorFive.class.isAssignableFrom(interceptor.getClass()));

        assertThat(hasInterceptor, is(true));
    }


    private int sizeOf(PriorityBag<?> priorityBag) {
        return (int) priorityBag.stream().count();
    }

    @GrpcInterceptorBinding
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface InterceptorOne {
    }

    @GrpcInterceptorBinding
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface InterceptorTwo {
    }

    @GrpcInterceptorBinding
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface InterceptorThree {
        int id() default 0;
    }

    @RpcService
    @InterceptorOne
    @ApplicationScoped
    public static class InterceptedServiceOne {
        @Unary
        public void foo() {
        }
    }

    @RpcService
    @ApplicationScoped
    public static class InterceptedServiceTwo {
        @Unary
        @InterceptorTwo
        public void foo() {
        }

        @Unary
        public void bar() {
        }
    }

    @RpcService
    @InterceptorOne
    @ApplicationScoped
    public static class InterceptedServiceThree {
        @Unary
        @InterceptorTwo
        public void foo() {
        }

        @Unary
        public void bar() {
        }
    }

    @RpcService
    @InterceptorThree(id = 2)
    @ApplicationScoped
    public static class InterceptedServiceFour {
        @Unary
        public void foo() {
        }
    }

    @RpcService
    @ApplicationScoped
    @GrpcInterceptors({ServerInterceptorOne.class, ServerInterceptorTwo.class})
    public static class InterceptedServiceFive {
        @Unary
        public void foo() {
        }
    }

    @RpcService
    @ApplicationScoped
    public static class InterceptedServiceSix {
        @Unary
        @GrpcInterceptors({ServerInterceptorOne.class, ServerInterceptorTwo.class})
        public void foo() {
        }
    }

    @RpcService
    @ApplicationScoped
    @GrpcInterceptors(ServerInterceptorFive.class)
    public static class InterceptedServiceSeven {
        @Unary
        public void foo() {
        }
    }

    @RpcService
    @ApplicationScoped
    public static class InterceptedServiceEight {
        @Unary
        @GrpcInterceptors(ServerInterceptorFive.class)
        public void foo() {
        }
    }

    public static class BaseServerInterceptor
            implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall,
                                                                     Metadata metadata,
                                                                     ServerCallHandler<ReqT, RespT> serverCallHandler) {
            return null;
        }
    }

    @GrpcInterceptor
    @InterceptorOne
    @ApplicationScoped
    public static class ServerInterceptorOne
            extends BaseServerInterceptor {
    }

    @GrpcInterceptor
    @InterceptorTwo
    @ApplicationScoped
    public static class ServerInterceptorTwo
            extends BaseServerInterceptor {
    }

    @GrpcInterceptor
    @InterceptorThree(id = 1)
    @ApplicationScoped
    public static class ServerInterceptorThree
            extends BaseServerInterceptor {
    }

    @GrpcInterceptor
    @InterceptorThree(id = 2)
    @ApplicationScoped
    public static class ServerInterceptorFour
            extends BaseServerInterceptor {
    }

    public static class ServerInterceptorFive
            extends BaseServerInterceptor {
    }
}
