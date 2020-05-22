/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.metrics;

import java.util.List;
import java.util.stream.Collectors;

import io.helidon.grpc.metrics.GrpcMetrics;
import io.helidon.grpc.server.GrpcService;
import io.helidon.grpc.server.MethodDescriptor;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.metrics.RegistryFactory;
import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.core.Unary;

import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;


public class MetricsConfigurerTest {

    private static MetricRegistry registry;

    @BeforeAll
    public static void setup() {
        registry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);
    }

    @Test
    public void shouldAddCounterMetricFromClassAnnotation() {
        Class<?> serviceClass = ServiceOne.class;
        Class<?> annotatedClass = ServiceOne.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceOne());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("counted");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors.size(), is(1));
        assertThat(methodInterceptors.get(0), is(instanceOf(GrpcMetrics.class)));
        assertThat(((GrpcMetrics) methodInterceptors.get(0)).metricType(), is(MetricType.COUNTER));
        assertThat(registry.getCounters().get(new MetricID(ServiceOne.class.getName() + ".counted")), is(notNullValue()));
    }

    @Test
    public void shouldAddMeterMetricFromClassAnnotation() {
        Class<?> serviceClass = ServiceOne.class;
        Class<?> annotatedClass = ServiceOne.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceOne());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("metered");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors.size(), is(1));
        assertThat(methodInterceptors.get(0), is(instanceOf(GrpcMetrics.class)));
        assertThat(((GrpcMetrics) methodInterceptors.get(0)).metricType(), is(MetricType.METERED));
        assertThat(registry.getMeters().get(new MetricID(ServiceOne.class.getName() + ".metered")), is(notNullValue()));
    }

    @Test
    public void shouldAddTimerMetricFromClassAnnotation() {
        Class<?> serviceClass = ServiceOne.class;
        Class<?> annotatedClass = ServiceOne.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceOne());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("timed");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors.size(), is(1));
        assertThat(methodInterceptors.get(0), is(instanceOf(GrpcMetrics.class)));
        assertThat(((GrpcMetrics) methodInterceptors.get(0)).metricType(), is(MetricType.TIMER));
        assertThat(registry.getTimers().get(new MetricID(ServiceOne.class.getName() + ".timed")), is(notNullValue()));
    }

    @Test
    public void shouldAddOverriddenCounterMetricFromSuperClassAnnotation() {
        Class<?> serviceClass = ServiceThree.class;
        Class<?> annotatedClass = ServiceOne.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceThree());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("counted");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors.size(), is(1));
        assertThat(methodInterceptors.get(0), is(instanceOf(GrpcMetrics.class)));
        assertThat(((GrpcMetrics) methodInterceptors.get(0)).metricType(), is(MetricType.COUNTER));
        assertThat(registry.getCounters().get(new MetricID(ServiceThree.class.getName() + ".foo")), is(notNullValue()));
    }

    @Test
    public void shouldNotAddMetricsToOverriddenSubclassMethod() {
        Class<?> serviceClass = ServiceThree.class;
        Class<?> annotatedClass = ServiceOne.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceThree());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("timed");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors, is(emptyIterable()));
    }


    @Test
    public void shouldNotAddMetricsToNonOverriddenSubclassMethod() {
        Class<?> serviceClass = ServiceThree.class;
        Class<?> annotatedClass = ServiceOne.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceThree());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("metered");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors, is(emptyIterable()));
    }

    @Test
    public void shouldIgnoreCounterMetricFromInterfaceAnnotation() {
        Class<?> serviceClass = ServiceTwoImpl.class;
        Class<?> annotatedClass = ServiceTwo.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceTwoImpl());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("counted");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors, is(emptyIterable()));
    }

    @Test
    public void shouldIgnoreMeterMetricFromInterfaceAnnotation() {
        Class<?> serviceClass = ServiceTwoImpl.class;
        Class<?> annotatedClass = ServiceTwo.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceTwoImpl());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("metered");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors, is(emptyIterable()));
    }

    @Test
    public void shouldIgnoreTimerMetricFromInterfaceAnnotation() {
        Class<?> serviceClass = ServiceTwoImpl.class;
        Class<?> annotatedClass = ServiceTwo.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceTwoImpl());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("timed");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors, is(emptyIterable()));
    }


    @Grpc
    public static class ServiceOne
            implements GrpcService {
        @Override
        public void update(ServiceDescriptor.Rules rules) {
            rules.unary("counted", this::counted)
                 .unary("timed", this::timed)
                 .unary("metered", this::metered);
        }

        @Unary
        @Counted
        public void counted(String request, StreamObserver<String> response) {
        }

        @Unary
        @Timed
        public void timed(String request, StreamObserver<String> response) {
        }

        @Unary
        @Metered
        public void metered(String request, StreamObserver<String> response) {
        }
    }

    @Grpc
    @SuppressWarnings("CdiManagedBeanInconsistencyInspection")
    public interface ServiceTwo {

        @Unary
        @Counted
        void counted(String request, StreamObserver<String> response);

        @Unary
        @Timed
        void timed(String request, StreamObserver<String> response);

        @Unary
        @Metered
        void metered(String request, StreamObserver<String> response);
    }

    public static class ServiceTwoImpl
            implements ServiceTwo, GrpcService {
        @Override
        public void update(ServiceDescriptor.Rules rules) {
            rules.unary("counted", this::counted)
                 .unary("timed", this::timed)
                 .unary("metered", this::metered);
        }

        @Override
        public void counted(String request, StreamObserver<String> response) {
        }

        @Override
        public void timed(String request, StreamObserver<String> response) {
        }

        @Override
        public void metered(String request, StreamObserver<String> response) {
        }
    }

    public static class ServiceThree
            extends ServiceOne {
        @Override
        @Counted(name = "foo")
        public void counted(String request, StreamObserver<String> response) {
            super.counted(request, response);
        }

        @Override
        public void timed(String request, StreamObserver<String> response) {
            super.timed(request, response);
        }
    }
}
