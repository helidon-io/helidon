package io.helidon.grpc.server;


import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
public class HealthClient
    {
    public static void main(String[] args) throws Exception
        {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408).usePlaintext().build();

        HealthGrpc.HealthBlockingStub health = HealthGrpc.newBlockingStub(channel);
        System.out.println(health.check(HealthCheckRequest.newBuilder().setService("GreetService").build()));
        System.out.println(health.check(HealthCheckRequest.newBuilder().setService("FooService").build()));
        }
    }
