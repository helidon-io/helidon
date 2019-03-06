package io.helidon.grpc.server;


import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannelBuilder;

import io.helidon.grpc.server.test.Greet.GreetRequest;
import io.helidon.grpc.server.test.Greet.SetGreetingRequest;
import io.helidon.grpc.server.test.GreetServiceGrpc;
import io.helidon.tracing.TracerBuilder;
import io.opentracing.Tracer;

import java.net.URI;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
public class GreetClient
    {
    public static void main(String[] args) throws Exception
        {
        Tracer tracer = (Tracer) TracerBuilder.create("Client")
                .collectorUri(URI.create("http://localhost:9411/api/v2/spans"))
                .build();

        ClientTracingInterceptor tracingInterceptor = ClientTracingInterceptor.builder(tracer)
                .withVerbosity().withTracedAttributes(ClientRequestAttribute.ALL_CALL_OPTIONS).build();

        Channel channel = ClientInterceptors.intercept(ManagedChannelBuilder.forAddress("localhost", 1408).usePlaintext().build(), tracingInterceptor);

        GreetServiceGrpc.GreetServiceBlockingStub greetSvc = GreetServiceGrpc.newBlockingStub(channel);
        System.out.println(greetSvc.greet(GreetRequest.newBuilder().setName("Aleks").build()));
        System.out.println(greetSvc.setGreeting(SetGreetingRequest.newBuilder().setGreeting("Ciao").build()));
        System.out.println(greetSvc.greet(GreetRequest.newBuilder().setName("Aleks").build()));


        Thread.sleep(5000);
        }
    }
