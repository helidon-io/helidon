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

package io.helidon.grpc.client;

import java.util.function.Consumer;

import io.helidon.grpc.core.JavaMarshaller;

import io.grpc.MethodDescriptor.MethodType;
import org.eclipse.microprofile.metrics.MetricType;
import org.junit.jupiter.api.Test;
import services.TreeMapService;

import static io.helidon.grpc.client.GrpcClientTestUtil.getMetricConfigurer;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static services.TreeMapService.Person;

public class ClientServiceDescriptorTest {

    static ClientServiceDescriptor.Builder createEmptyTreeMapServiceBuilder() {
        return ClientServiceDescriptor.builder("TreeMapService", TreeMapService.class);
    }

    // Custom built ClientServiceDescriptor

    @Test
    public void shouldHaveZeroMethodsByDefault() {
        ClientServiceDescriptor svcDesc = createEmptyTreeMapServiceBuilder().build();
        assertThat(svcDesc.methods().size(), equalTo(0));
        assertThat(svcDesc.serviceClass(), equalTo(TreeMapService.class));
        assertThat(svcDesc.serviceName(), equalTo("TreeMapService"));
        assertThat(svcDesc.metricType(), equalTo(MetricType.INVALID));
        assertThat(svcDesc.proto(), equalTo(null));
        assertThat(svcDesc.context().size(), equalTo(0));
        assertThat(svcDesc.interceptors().size(), equalTo(0));
    }

    @Test
    public void createUnaryMethod() {

        for (MetricType mt : MetricType.values()) {
            Consumer<ClientMethodDescriptor.Config<Object, Object>> metricConfigurer = getMetricConfigurer(mt);
            String methodName = "get" + (mt == MetricType.INVALID ? "" : mt.toString());
            ClientServiceDescriptor csd = createEmptyTreeMapServiceBuilder()
                    .unary(methodName, metricConfigurer)
                    .build();

            ClientMethodDescriptor cmd = csd.method(methodName);
            assertThat(cmd, notNullValue());
            assertThat(cmd.descriptor().getFullMethodName(), equalTo("TreeMapService/" + methodName));
            assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getType(), equalTo(MethodType.UNARY));
            assertThat(cmd.metricType(), equalTo(mt));

            // Check Marshallers
            assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
        }
    }

    @Test
    public void createClientStreamingMethod() {

        for (MetricType mt : MetricType.values()) {
            Consumer<ClientMethodDescriptor.Config<Object, Object>> metricConfigurer = getMetricConfigurer(mt);
            String methodName = "get" + (mt == MetricType.INVALID ? "" : mt.toString());
            ClientServiceDescriptor csd = createEmptyTreeMapServiceBuilder()
                    .clientStreaming(methodName, metricConfigurer)
                    .build();

            ClientMethodDescriptor cmd = csd.method(methodName);
            assertThat(cmd, notNullValue());
            assertThat(cmd.descriptor().getFullMethodName(), equalTo("TreeMapService/" + methodName));
            assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getType(), equalTo(MethodType.CLIENT_STREAMING));
            assertThat(cmd.metricType(), equalTo(mt));

            // Check Marshallers
            assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
        }
    }

    @Test
    public void createServerStreamingMethod() {

        for (MetricType mt : MetricType.values()) {
            Consumer<ClientMethodDescriptor.Config<Object, Object>> metricConfigurer = getMetricConfigurer(mt);
            String methodName = "get" + (mt == MetricType.INVALID ? "" : mt.toString());
            ClientServiceDescriptor csd = createEmptyTreeMapServiceBuilder()
                    .serverStreaming(methodName, metricConfigurer)
                    .build();

            ClientMethodDescriptor cmd = csd.method(methodName);
            assertThat(cmd, notNullValue());
            assertThat(cmd.descriptor().getFullMethodName(), equalTo("TreeMapService/" + methodName));
            assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getType(), equalTo(MethodType.SERVER_STREAMING));
            assertThat(cmd.metricType(), equalTo(mt));

            // Check Marshallers
            assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
        }
    }

    @Test
    public void createBidiStreaming() {

        for (MetricType mt : MetricType.values()) {
            Consumer<ClientMethodDescriptor.Config<Object, Object>> metricConfigurer = getMetricConfigurer(mt);
            String methodName = "get" + (mt == MetricType.INVALID ? "" : mt.toString());
            ClientServiceDescriptor csd = createEmptyTreeMapServiceBuilder()
                    .bidirectional(methodName, metricConfigurer)
                    .build();

            ClientMethodDescriptor cmd = csd.method(methodName);
            assertThat(cmd, notNullValue());
            assertThat(cmd.descriptor().getFullMethodName(), equalTo("TreeMapService/" + methodName));
            assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getType(), equalTo(MethodType.BIDI_STREAMING));
            assertThat(cmd.metricType(), equalTo(mt));

            // Check Marshallers
            assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
        }
    }

    @Test
    public void registerUnaryMethod() {

        String methodName = "get";
        ClientMethodDescriptor<Integer, Person> cmd =
                ClientMethodDescriptor.unary("TreeMapService/" + methodName, Integer.class, Person.class);
        cmd = cmd.toBuilder().metered().build();
        ClientServiceDescriptor csd = createEmptyTreeMapServiceBuilder()
                .registerMethod(cmd)
                .build();

        cmd = csd.method(methodName);
        assertThat(cmd, notNullValue());
        assertThat(cmd.descriptor().getFullMethodName(), equalTo("TreeMapService/" + methodName));
        assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
        assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
        assertThat(cmd.descriptor().getType(), equalTo(MethodType.UNARY));
        assertThat(cmd.metricType(), equalTo(MetricType.METERED));

        // Check Marshallers
        assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
        assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
    }

    @Test
    public void registerClientStreamingMethod() {

        String methodName = "get";
        ClientMethodDescriptor<Integer, Person> cmd =
                ClientMethodDescriptor.clientStreaming("Foo/Bar/" + methodName, Integer.class, Person.class);
        cmd = cmd.toBuilder().metered().build();
        ClientServiceDescriptor csd = createEmptyTreeMapServiceBuilder()
                .registerMethod(cmd)
                .build();

        cmd = csd.method(methodName);
        assertThat(cmd, notNullValue());
        assertThat(cmd.descriptor().getFullMethodName(), equalTo("TreeMapService/" + methodName));
        assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
        assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
        assertThat(cmd.descriptor().getType(), equalTo(MethodType.CLIENT_STREAMING));
        assertThat(cmd.metricType(), equalTo(MetricType.METERED));

        // Check Marshallers
        assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
        assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
    }

}
