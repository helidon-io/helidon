package io.helidon.security.integration.grpc;


import io.grpc.Attributes;
import io.grpc.CallCredentials;
import io.grpc.CallCredentials2;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.helidon.security.integration.grpc.test.Greet;
import io.helidon.security.integration.grpc.test.GreetServiceGrpc;

import java.util.Base64;
import java.util.concurrent.Executor;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
public class SecureGreetClient
    {
    public static void main(String[] args) throws Exception
        {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408)
                .usePlaintext()
                .build();


        GreetServiceGrpc.GreetServiceBlockingStub greetSvc = GreetServiceGrpc.newBlockingStub(channel);

        if (args.length > 1)
            {
            CallCredentials credentials = new BasicAuthCallCredentials(args[0], args[1]);

            greetSvc = greetSvc.withCallCredentials(credentials);
            }

        System.out.println(greetSvc.greet(Greet.GreetRequest.newBuilder().setName("Aleks").build()));
        System.out.println(greetSvc.setGreeting(Greet.SetGreetingRequest.newBuilder().setGreeting("Zdravo").build()));
        System.out.println(greetSvc.greet(Greet.GreetRequest.newBuilder().setName("Aleks").build()));
        }

    public static class BasicAuthCallCredentials
            extends CallCredentials2
        {
        private String basicAuth;

        public BasicAuthCallCredentials(String username, String password)
            {
            String usernameAndPassword = username + ":" + password;

            basicAuth = Base64.getEncoder().encodeToString(usernameAndPassword.getBytes());
            }

        @Override
        public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier applier)
            {
            Metadata metadata = new Metadata();

            metadata.put(GrpcSecurity.AUTHORIZATION, "Basic " + basicAuth);

            applier.apply(metadata);
            }

        @Override
        public void thisUsesUnstableApi()
            {
            }
        }
    }
