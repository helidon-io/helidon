package io.helidon.grpc.server;


import io.grpc.BindableService;
import java.util.List;


/**
 * @author Aleksandar Seovic  2019.02.13
 */
public class GrpcRoutingImpl
        implements GrpcRouting
    {
    private List<BindableService> bindableServices;

    GrpcRoutingImpl(List<BindableService> bindableServices)
        {
        this.bindableServices = bindableServices;
        }

    public Iterable<BindableService> services()
        {
        return bindableServices;
        }
    }
