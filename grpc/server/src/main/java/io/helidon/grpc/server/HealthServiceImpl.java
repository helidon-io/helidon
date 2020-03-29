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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.Status;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.services.HealthStatusManager;
import io.grpc.stub.StreamObserver;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse.State;

/**
 * An implementation of the {@link HealthGrpc} service.
 */
class HealthServiceImpl
        extends HealthGrpc.HealthImplBase {

    /**
     * A map of {@link HealthCheck}s keyed by service name.
     */
    private final Map<String, HealthCheck> mapHealthChecks = new ConcurrentHashMap<>();

    private HealthServiceImpl() {
        // register the empty service name to represent the global health check
        // see: https://github.com/grpc/grpc/blob/master/doc/health-checking.md
        mapHealthChecks.put(HealthStatusManager.SERVICE_NAME_ALL_SERVICES,
                            ConstantHealthCheck.up(HealthStatusManager.SERVICE_NAME_ALL_SERVICES));
    }

    /**
     * Create a {@link HealthServiceImpl}.
     */
    static HealthServiceImpl create() {
        return new HealthServiceImpl();
    }

    /**
     * Add a {@link HealthCheck}.
     * @param name         the name of the service that the health check is for
     * @param healthCheck  the {@link HealthCheck} implementation
     */
    void add(String name, HealthCheck healthCheck) {
        mapHealthChecks.put(name, healthCheck);
    }

    /**
     * Obtain the collection of registered {@link HealthCheck}s.
     *
     * @return  the collection of registered {@link HealthCheck}s
     */
    Collection<HealthCheck> healthChecks() {
        return mapHealthChecks.values();
    }

    @Override
    public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        String service = request.getService();
        HealthCheck check = mapHealthChecks.get(service);

        if (check == null) {
            // If no health check is registered for the requested service then respond with a not found error.
            // See method comments:
            // https://github.com/grpc/grpc-java/blob/7df2d5feebf8bc5ecfcea3edba290db500382dcf/services/src/generated/main/grpc/io/grpc/health/v1/HealthGrpc.java#L149
            String message = "Service '" + service + "' does not exist or does not have a registered health check";
            responseObserver.onError(Status.NOT_FOUND.withDescription(message).asException());
        } else {
            responseObserver.onNext(toHealthCheckResponse(check.call()));
            responseObserver.onCompleted();
        }
    }

    private HealthCheckResponse toHealthCheckResponse(HealthCheckResponse.ServingStatus status) {
        return HealthCheckResponse.newBuilder().setStatus(status).build();
    }

    private HealthCheckResponse toHealthCheckResponse(org.eclipse.microprofile.health.HealthCheckResponse response) {
        return response.getState().equals(State.UP)
                ? toHealthCheckResponse(HealthCheckResponse.ServingStatus.SERVING)
                : toHealthCheckResponse(HealthCheckResponse.ServingStatus.NOT_SERVING);
    }
}
