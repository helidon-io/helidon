/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.server;

import io.helidon.metrics.MetricsSupport;
import io.helidon.metrics.RegistryFactory;
import io.helidon.webserver.Routing;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A test for the {@link io.helidon.grpc.server.GrpcMetrics} interceptor.
 * <p>
 * This test runs as an integration test because it causes Helidon metrics
 * to be initialised which may impact other tests that rely on metrics being
 * configured a specific way.
 *
 * @author Jonathan Knight
 */
@SuppressWarnings("unchecked")
public class GrpcMetricsInterceptorIT {

    private static MetricRegistry vendorRegsistry;

    private static MetricRegistry appRegistry;

    private static Meter vendorMeter;

    private static Counter vendorCounter;

    private long vendorMeterCount;

    private long vendorCount;

    @BeforeAll
    static void configureMetrics() {
        Routing.Rules rules = Routing.builder().get("metrics");
        MetricsSupport.create().update(rules);

        vendorRegsistry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.VENDOR);
        appRegistry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);
        vendorMeter = vendorRegsistry.meter("grpc.requests.meter");
        vendorCounter = vendorRegsistry.counter("grpc.requests.count");
    }

    @BeforeEach
    public void setup() {
        // obtain the current counts for vendor metrics so that we can assert
        // the count in each test
        vendorCount = vendorCounter.getCount();
        vendorMeterCount = vendorMeter.getCount();
    }

    @Test
    public void shouldUseCountedMetric() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .unary("barCounted", this::dummyUnary)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("barCounted");
        GrpcMetrics metrics = GrpcMetrics.counted();

        metrics.setServiceDescriptor(descriptor);

        ServerCall<String, String> call = call(metrics, methodDescriptor);

        call.close(Status.OK, new Metadata());

        Counter appCounter = appRegistry.counter("Foo.barCounted");

        assertVendorMetrics();
        assertThat(appCounter.getCount(), is(1L));
    }

    @Test
    public void shouldUseHistogramMetric() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .unary("barHistogram", this::dummyUnary)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("barHistogram");
        GrpcMetrics metrics = GrpcMetrics.histogram();

        metrics.setServiceDescriptor(descriptor);

        ServerCall<String, String> call = call(metrics, methodDescriptor);

        call.close(Status.OK, new Metadata());

        Histogram appHistogram = appRegistry.histogram("Foo.barHistogram");

        assertVendorMetrics();
        assertThat(appHistogram.getCount(), is(1L));
    }

    @Test
    public void shouldUseMeteredMetric() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .unary("barMetered", this::dummyUnary)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("barMetered");
        GrpcMetrics metrics = GrpcMetrics.metered();

        metrics.setServiceDescriptor(descriptor);

        ServerCall<String, String> call = call(metrics, methodDescriptor);

        call.close(Status.OK, new Metadata());

        Meter appMeter = appRegistry.meter("Foo.barMetered");

        assertVendorMetrics();
        assertThat(appMeter.getCount(), is(1L));
    }

    @Test
    public void shouldUseTimerMetric() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .unary("barTimed", this::dummyUnary)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("barTimed");
        GrpcMetrics metrics = GrpcMetrics.timed();

        metrics.setServiceDescriptor(descriptor);

        ServerCall<String, String> call = call(metrics, methodDescriptor);

        call.close(Status.OK, new Metadata());

        Timer appTimer = appRegistry.timer("Foo.barTimed");

        assertVendorMetrics();
        assertThat(appTimer.getCount(), is(1L));
    }

    @Test
    public void shouldUseServiceOverrideMetric() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .metered()
                .unary("barServiceOverride", this::dummyUnary)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("barServiceOverride");
        GrpcMetrics metrics = GrpcMetrics.counted();

        metrics.setServiceDescriptor(descriptor);

        ServerCall<String, String> call = call(metrics, methodDescriptor);

        call.close(Status.OK, new Metadata());

        Meter appMeter = appRegistry.meter("Foo.barServiceOverride");

        assertVendorMetrics();
        assertThat(appMeter.getCount(), is(1L));
    }

    @Test
    public void shouldUseMethodOverrideToSetCounterMetric() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .metered()
                .unary("barOverrideCount", this::dummyUnary, MethodDescriptor.Config::counted)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("barOverrideCount");
        GrpcMetrics metrics = GrpcMetrics.counted();

        metrics.setServiceDescriptor(descriptor);

        ServerCall<String, String> call = call(metrics, methodDescriptor);

        call.close(Status.OK, new Metadata());

        Counter appCounter = appRegistry.counter("Foo.barOverrideCount");

        assertVendorMetrics();
        assertThat(appCounter.getCount(), is(1L));
    }

    @Test
    public void shouldUseMethodOverrideToSetHistogramMetric() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .metered()
                .unary("barOverrideHistogram", this::dummyUnary, MethodDescriptor.Config::histogram)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("barOverrideHistogram");
        GrpcMetrics metrics = GrpcMetrics.counted();

        metrics.setServiceDescriptor(descriptor);

        ServerCall<String, String> call = call(metrics, methodDescriptor);

        call.close(Status.OK, new Metadata());

        Histogram appHistogram = appRegistry.histogram("Foo.barOverrideHistogram");

        assertVendorMetrics();
        assertThat(appHistogram.getCount(), is(1L));
    }

    @Test
    public void shouldUseMethodOverrideToSetMeterMetric() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .metered()
                .unary("barOverrideMeter", this::dummyUnary, MethodDescriptor.Config::metered)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("barOverrideMeter");
        GrpcMetrics metrics = GrpcMetrics.counted();

        metrics.setServiceDescriptor(descriptor);

        ServerCall<String, String> call = call(metrics, methodDescriptor);

        call.close(Status.OK, new Metadata());

        Meter appMeter = appRegistry.meter("Foo.barOverrideMeter");

        assertVendorMetrics();
        assertThat(appMeter.getCount(), is(1L));
    }

    @Test
    public void shouldUseMethodOverrideToSetTimerMetric() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .metered()
                .unary("barOverrideTimer", this::dummyUnary, MethodDescriptor.Config::timed)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("barOverrideTimer");
        GrpcMetrics metrics = GrpcMetrics.counted();

        metrics.setServiceDescriptor(descriptor);

        ServerCall<String, String> call = call(metrics, methodDescriptor);

        call.close(Status.OK, new Metadata());

        Timer appTimer = appRegistry.timer("Foo.barOverrideTimer");

        assertVendorMetrics();
        assertThat(appTimer.getCount(), is(1L));
    }

    @Test
    public void shouldUseMethodOverrideToDisableMetrics() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .metered()
                .unary("barOverrideOff", this::dummyUnary, MethodDescriptor.Config::disableMetrics)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("barOverrideOff");
        GrpcMetrics metrics = GrpcMetrics.counted();

        metrics.setServiceDescriptor(descriptor);

        ServerCall<String, String> call = call(metrics, methodDescriptor);

        call.close(Status.OK, new Metadata());

        assertVendorMetrics();
        assertThat(appRegistry.getNames().contains("barOverrideOff"), is(false));
    }

    private ServerCall<String, String> call(GrpcMetrics metrics, MethodDescriptor methodDescriptor) {
        Metadata headers = new Metadata();
        ServerCall<String, String> call = mock(ServerCall.class);
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
        ServerCall.Listener<String> listener = mock(ServerCall.Listener.class);

        when(call.getMethodDescriptor()).thenReturn(methodDescriptor.descriptor());
        when(next.startCall(any(ServerCall.class), any(Metadata.class))).thenReturn(listener);

        ServerCall.Listener<String> result = metrics.interceptCall(call, headers, next);

        assertThat(result, is(sameInstance(listener)));

        ArgumentCaptor<ServerCall> captor = ArgumentCaptor.forClass(ServerCall.class);

        verify(next).startCall(captor.capture(), same(headers));

        ServerCall<String, String> wrappedCall = captor.getValue();

        assertThat(wrappedCall, is(notNullValue()));

        return wrappedCall;
    }

    private void assertVendorMetrics() {
        Meter meter = vendorRegsistry.meter("grpc.requests.meter");
        Counter counter = vendorRegsistry.counter("grpc.requests.count");

        assertThat(meter.getCount(), is(vendorMeterCount + 1));
        assertThat(counter.getCount(), is(vendorCount + 1));
    }

    private GrpcService createMockService() {
        GrpcService service = mock(GrpcService.class);

        when(service.name()).thenReturn("Foo");

        return service;
    }

    private void dummyUnary(String request, StreamObserver<String> observer) {
    }
}
