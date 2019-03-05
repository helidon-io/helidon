package io.helidon.grpc.server;


import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;

import io.grpc.stub.StreamObserver;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse.State;


/**
 * @author Aleksandar Seovic  2019.03.05
 */
class HealthServiceImpl
        extends HealthGrpc.HealthImplBase
    {
    private Map<String, HealthCheck> mapHealthChecks = new ConcurrentHashMap<>();

    void add(String name, HealthCheck healthCheck)
        {
        mapHealthChecks.put(name, healthCheck);
        }

    Collection<HealthCheck> healthChecks()
        {
        return mapHealthChecks.values();
        }

    public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver)
        {
        String service = request.getService();
        HealthCheck check = mapHealthChecks.get(service);
        if (check == null)
            {
            responseObserver.onNext(toHealthCheckResponse(HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN));
            responseObserver.onCompleted();
            }
        else
            {
            responseObserver.onNext(toHealthCheckResponse(check.call()));
            responseObserver.onCompleted();
            }
        }

    private HealthCheckResponse toHealthCheckResponse(HealthCheckResponse.ServingStatus status)
        {
        return HealthCheckResponse.newBuilder().setStatus(status).build();
        }

    private HealthCheckResponse toHealthCheckResponse(org.eclipse.microprofile.health.HealthCheckResponse response)
        {
        return response.getState().equals(State.UP)
               ? toHealthCheckResponse(HealthCheckResponse.ServingStatus.SERVING)
               : toHealthCheckResponse(HealthCheckResponse.ServingStatus.NOT_SERVING);
        }
    }
