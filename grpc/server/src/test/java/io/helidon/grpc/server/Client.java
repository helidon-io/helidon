package io.helidon.grpc.server;


import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.helidon.grpc.server.test.Greet;
import io.helidon.grpc.server.test.GreetServiceGrpc;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
public class Client
    {
    public static void main(String[] args)
        {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();

        GreetServiceGrpc.GreetServiceBlockingStub greetSvc = GreetServiceGrpc.newBlockingStub(channel);
        System.out.println(greetSvc.greet(Greet.GreetRequest.newBuilder().setName("Aleks").build()));
        System.out.println(greetSvc.setGreeting(Greet.SetGreetingRequest.newBuilder().setGreeting("Zdravo").build()));
        System.out.println(greetSvc.greet(Greet.GreetRequest.newBuilder().setName("Aleks").build()));
        }
    }
