package io.helidon.grpc.server;


import io.grpc.ServerInterceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;


/**
 * @author Aleksandar Seovic  2019.02.13
 */
public class GrpcRoutingImpl
        implements GrpcRouting
    {
    private List<GrpcService.ServiceConfig> services;

    private List<ServerInterceptor> interceptors;

    GrpcRoutingImpl(List<GrpcService.ServiceConfig> services, List<ServerInterceptor> interceptors)
        {
        this.services = new ArrayList<>(Objects.requireNonNull(services));
        this.interceptors = new ArrayList<>(Objects.requireNonNull(interceptors));
        }

    @Override
    public List<GrpcService.ServiceConfig> services()
        {
        return services;
        }

    @Override
    public List<ServerInterceptor> interceptors()
        {
        return interceptors;
        }
    }
