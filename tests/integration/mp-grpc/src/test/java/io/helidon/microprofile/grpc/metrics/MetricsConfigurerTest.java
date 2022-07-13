/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.core.Unary;

import io.grpc.ServerInterceptor;

import io.helidon.microprofile.grpc.server.JavaMarshaller;
import io.helidon.microprofile.grpc.server.test.Services;
import io.helidon.microprofile.metrics.MetricsCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import io.grpc.stub.StreamObserver;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;

/**
 * Exercises the metrics configurer.
 *
 * Because the metrics CDI extension is (as of 3.x) responsible for registering metrics, even gRPC-inspired ones, we need to
 * start the container. We cannot currently allow the gRPC CDI extension to start because it tries to register both ServiceOne and
 * ServiceThree under the same name, ServiceOne, because ServiceThree extends ServiceOne. The attempt to register two services
 * with the same name fails. So we turn off discovery and explicitly add in the beans and extensions we need.
 */
@HelidonTest
@DisableDiscovery
@AddBean(MetricsConfigurerTest.ServiceOne.class)
@AddBean(MetricsConfigurerTest.ServiceThree.class)
@AddBean(MetricsConfigurerTest.ServiceTwoProducer.class)
@AddBean(NonGrpcMetricAnnotatedBean.class)
@AddExtension(MetricsCdiExtension.class)
@AddExtension(ServerCdiExtension.class) // needed for MetricsCdiExtension
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(GrpcMetricsCdiExtension.class)
public class MetricsConfigurerTest {

    private static MetricRegistry registry;

    @Inject
    private NonGrpcMetricAnnotatedBean nonGrpcMetricAnnotatedBean;

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
    public void shouldAddSimpleTimerMetricFromClassAnnotation() {
        Class<?> serviceClass = ServiceOne.class;
        Class<?> annotatedClass = ServiceOne.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceOne());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("simplyTimed");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors.size(), is(1));
        assertThat(methodInterceptors.get(0), is(instanceOf(GrpcMetrics.class)));
        assertThat(((GrpcMetrics) methodInterceptors.get(0)).metricType(), is(MetricType.SIMPLE_TIMER));
        assertThat(registry.getSimpleTimers().get(new MetricID(ServiceOne.class.getName() + ".simplyTimed")), is(notNullValue()));
    }

    @Test
    public void shouldAddConcurrentGaugeMetricFromClassAnnotation() {
        Class<?> serviceClass = ServiceOne.class;
        Class<?> annotatedClass = ServiceOne.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceOne());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("concurrentGauge");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors.size(), is(1));
        assertThat(methodInterceptors.get(0), is(instanceOf(GrpcMetrics.class)));
        assertThat(((GrpcMetrics) methodInterceptors.get(0)).metricType(), is(MetricType.CONCURRENT_GAUGE));
        assertThat(registry.getConcurrentGauges().get(new MetricID(ServiceOne.class.getName() + ".concurrentGauge")),
                is(notNullValue()));
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

    @Test
    public void shouldIgnoreSimpleTimerMetricFromInterfaceAnnotation() {
        Class<?> serviceClass = ServiceTwoImpl.class;
        Class<?> annotatedClass = ServiceTwo.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceTwoImpl());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("simplyTimed");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors, is(emptyIterable()));
    }
    @Test
    public void shouldIgnoreConcurrentGaugeMetricFromInterfaceAnnotation() {
        Class<?> serviceClass = ServiceTwoImpl.class;
        Class<?> annotatedClass = ServiceTwo.class;
        ServiceDescriptor.Builder builder = ServiceDescriptor.builder(new ServiceTwoImpl());

        MetricsConfigurer configurer = new MetricsConfigurer();
        configurer.accept(serviceClass, annotatedClass, builder);

        ServiceDescriptor descriptor = builder.build();
        List<ServerInterceptor> serviceInterceptors = descriptor.interceptors().stream().collect(Collectors.toList());
        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("concurrentGauge");
        List<ServerInterceptor> methodInterceptors = methodDescriptor.interceptors().stream().collect(Collectors.toList());
        assertThat(serviceInterceptors, is(emptyIterable()));
        assertThat(methodInterceptors, is(emptyIterable()));
    }

    @Test
    void checkNonGrpcResourceForMetrics() {

        // Make sure the gRPC metrics observer did not accidentally discard discoveries of legitimate,
        // non-gRPC metric annotations.
        Counter helloWorldClassLevelMessage = registry.getCounter(
                new MetricID(NonGrpcMetricAnnotatedBean.class.getName() + ".message"));
        assertThat("message counter declared at class level", helloWorldClassLevelMessage, is(notNullValue()));

        SimpleTimer messageWithArgSimpleTimer = registry.getSimpleTimer(
                new MetricID(NonGrpcMetricAnnotatedBean.MESSAGE_SIMPLE_TIMER));
        assertThat("messageWithArg simple timer at method level", messageWithArgSimpleTimer, is(notNullValue()));

        Counter helloWorldClassLevelMessageWithArg = registry.getCounter(
                new MetricID(NonGrpcMetricAnnotatedBean.class.getName() + ".messageWithArg"));
        assertThat("messageWithArg counter declared at class level",
                   helloWorldClassLevelMessageWithArg,
                   is(notNullValue()));

        // Now make sure the gRPC observer left the default interceptors in place.
        long before = helloWorldClassLevelMessage.getCount();
        nonGrpcMetricAnnotatedBean.message();
        assertThat("Change in counter on method with inherited metric annotation",
                   helloWorldClassLevelMessage.getCount() - before,
                   is(1L));

        before = helloWorldClassLevelMessageWithArg.getCount();
        nonGrpcMetricAnnotatedBean.messageWithArg("Joe");
        assertThat("Change in simple timer on method with explicit metric annotation",
                   helloWorldClassLevelMessageWithArg.getCount() - before,
                   is(1L));
    }

    @Grpc
    public static class ServiceOne
            implements GrpcService {
        @Override
        public void update(ServiceDescriptor.Rules rules) {
            rules.marshallerSupplier(new JavaMarshaller.Supplier())
                 .unary("counted", this::counted)
                 .unary("timed", this::timed)
                 .unary("metered", this::metered)
                 .unary("simplyTimed", this::simplyTimed)
                 .unary("concurrentGauge", this::concurrentGauge);
        }

        @Unary
        @Counted
        public void counted(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
        }

        @Unary
        @Timed
        public void timed(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
        }

        @Unary
        @Metered
        public void metered(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
        }

        @Unary
        @SimplyTimed
        public void simplyTimed(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
        }

        @Unary
        @ConcurrentGauge
        public void concurrentGauge(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
        }
    }

    @Grpc
    @SuppressWarnings("CdiManagedBeanInconsistencyInspection")
    public interface ServiceTwo extends GrpcService {

        @Unary
        @Counted
        void counted(Services.TestRequest request, StreamObserver<Services.TestResponse> response);

        @Unary
        @Timed
        void timed(Services.TestRequest request, StreamObserver<Services.TestResponse> response);

        @Unary
        @Metered
        void metered(Services.TestRequest request, StreamObserver<Services.TestResponse> response);

        @Unary
        @SimplyTimed
        void simplyTimed(Services.TestRequest request, StreamObserver<Services.TestResponse> response);

        @Unary
        @ConcurrentGauge
        void concurrentGauge(Services.TestRequest request, StreamObserver<Services.TestResponse> response);
    }

    public static class ServiceTwoImpl
            implements ServiceTwo {
        @Override
        public void update(ServiceDescriptor.Rules rules) {
            rules.marshallerSupplier(new JavaMarshaller.Supplier())
                    .unary("counted", this::counted)
                    .unary("timed", this::timed)
                    .unary("metered", this::metered)
                    .unary("simplyTimed", this::simplyTimed)
                    .unary("concurrentGauge", this::concurrentGauge);
        }

        @Override
        public void counted(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
        }

        @Override
        public void timed(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
        }

        @Override
        public void metered(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
        }

        @Override
        public void simplyTimed(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
        }

        @Override
        public void concurrentGauge(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
        }
    }

    @Typed(ServiceThree.class) // disambiguate from ServiceOne for CDI
    public static class ServiceThree
            extends ServiceOne {
        @Override
        @Counted(name = "foo")
        public void counted(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
            super.counted(request, response);
        }

        @Override
        public void timed(Services.TestRequest request, StreamObserver<Services.TestResponse> response) {
            super.timed(request, response);
        }
    }

    static class ServiceTwoProducer {

        @Produces
        public ServiceTwo create() {
            return new ServiceTwoImpl();
        }
    }
}
