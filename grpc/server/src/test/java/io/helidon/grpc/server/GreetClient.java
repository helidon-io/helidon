package io.helidon.grpc.server;


import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;

import io.helidon.grpc.server.test.Greet.GreetRequest;
import io.helidon.grpc.server.test.Greet.SetGreetingRequest;
import io.helidon.grpc.server.test.GreetServiceGrpc;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
public class GreetClient
    {
    public static void main(String[] args) throws Exception
        {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408).usePlaintext().build();

        GreetServiceGrpc.GreetServiceBlockingStub greetSvc = GreetServiceGrpc.newBlockingStub(channel);
        System.out.println(greetSvc.greet(GreetRequest.newBuilder().setName("Aleks").build()));
        System.out.println(greetSvc.setGreeting(SetGreetingRequest.newBuilder().setGreeting("Zdravo").build()));
        System.out.println(greetSvc.greet(GreetRequest.newBuilder().setName("Aleks").build()));
        }
    }
