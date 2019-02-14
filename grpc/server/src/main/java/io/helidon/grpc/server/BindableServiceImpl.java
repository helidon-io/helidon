package io.helidon.grpc.server;


import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
class BindableServiceImpl
        implements BindableService
    {
    private final ServerServiceDefinition serviceDefinition;

    BindableServiceImpl(ServerServiceDefinition serviceDefinition)
        {
        this.serviceDefinition = serviceDefinition;
        }

    public ServerServiceDefinition bindService()
        {
        return serviceDefinition;
        }
    }
