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

import java.util.List;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheck;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class HealthServiceImplTest {
    @Test
    public void shouldRequestCheckForUpService() {
        HealthServiceImpl healthService = HealthServiceImpl.create();
        String serviceName = "foo";
        HealthCheck check = ConstantHealthCheck.up(serviceName);
        HealthCheckRequest request = HealthCheckRequest.newBuilder().setService(serviceName).build();
        TestStreamObserver<HealthCheckResponse> observer = new TestStreamObserver<>();

        healthService.add(serviceName, check);

        healthService.check(request, observer);

        assertThat(observer.awaitTerminalEvent(), is(true));

        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(1);

        List<HealthCheckResponse> responses = observer.values();
        assertThat(responses.size(), is(1));
        HealthCheckResponse response = responses.get(0);
        assertThat(response.getStatus(), is(HealthCheckResponse.ServingStatus.SERVING));
    }

    @Test
    public void shouldRequestCheckForDownService() {
        HealthServiceImpl healthService = HealthServiceImpl.create();
        String serviceName = "foo";
        HealthCheck check = ConstantHealthCheck.down(serviceName);
        HealthCheckRequest request = HealthCheckRequest.newBuilder().setService(serviceName).build();
        TestStreamObserver<HealthCheckResponse> observer = new TestStreamObserver<>();

        healthService.add(serviceName, check);

        healthService.check(request, observer);

        assertThat(observer.awaitTerminalEvent(), is(true));

        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(1);

        List<HealthCheckResponse> responses = observer.values();
        assertThat(responses.size(), is(1));
        HealthCheckResponse response = responses.get(0);
        assertThat(response.getStatus(), is(HealthCheckResponse.ServingStatus.NOT_SERVING));
    }

    @Test
    public void shouldRequestCheckForGlobalService() {
        HealthServiceImpl healthService = HealthServiceImpl.create();
        String serviceName = "";
        HealthCheck check = ConstantHealthCheck.up(serviceName);
        HealthCheckRequest request = HealthCheckRequest.newBuilder().setService(serviceName).build();
        TestStreamObserver<HealthCheckResponse> observer = new TestStreamObserver<>();

        healthService.add(serviceName, check);

        healthService.check(request, observer);

        assertThat(observer.awaitTerminalEvent(), is(true));

        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(1);

        List<HealthCheckResponse> responses = observer.values();
        assertThat(responses.size(), is(1));
        HealthCheckResponse response = responses.get(0);
        assertThat(response.getStatus(), is(HealthCheckResponse.ServingStatus.SERVING));
    }

    @Test
    public void shouldRequestCheckWithoutServiceName() {
        HealthServiceImpl healthService = HealthServiceImpl.create();
        String serviceName = "";
        HealthCheck check = ConstantHealthCheck.up(serviceName);
        HealthCheckRequest request = HealthCheckRequest.newBuilder().build();
        TestStreamObserver<HealthCheckResponse> observer = new TestStreamObserver<>();

        healthService.add(serviceName, check);

        healthService.check(request, observer);

        assertThat(observer.awaitTerminalEvent(), is(true));

        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(1);

        List<HealthCheckResponse> responses = observer.values();
        assertThat(responses.size(), is(1));
        HealthCheckResponse response = responses.get(0);
        assertThat(response.getStatus(), is(HealthCheckResponse.ServingStatus.SERVING));
    }

    @Test
    public void shouldRequestCheckForUnknownService() {
        HealthServiceImpl healthService = HealthServiceImpl.create();
        String serviceName = "unknown";
        HealthCheckRequest request = HealthCheckRequest.newBuilder().setService(serviceName).build();
        TestStreamObserver<HealthCheckResponse> observer = new TestStreamObserver<>();

        healthService.check(request, observer);

        assertThat(observer.awaitTerminalEvent(), is(true));

        observer.assertError(this::isNotFoundError);
    }

    private boolean isNotFoundError(Throwable thrown) {
        if (thrown instanceof StatusException) {
            return ((StatusException) thrown).getStatus().getCode().equals(Status.NOT_FOUND.getCode());
        } else if (thrown instanceof StatusRuntimeException) {
            return ((StatusRuntimeException) thrown).getStatus().getCode().equals(Status.NOT_FOUND.getCode());
        } else {
            return false;
        }
    }
}
