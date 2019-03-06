package io.helidon.security.integration.grpc;


import io.grpc.stub.StreamObserver;
import io.helidon.config.Config;
import io.helidon.grpc.server.GrpcService;
import io.helidon.security.integration.grpc.test.Greet;

import java.util.Optional;


/**
 * @author Aleksandar Seovic  2019.02.11
 */
public class GreetService implements GrpcService
    {
    /**
     * The config value for the key {@code greeting}.
     */
    private String greeting;

    GreetService(Config config) {
        this.greeting = config.get("app.greeting").asString().orElse("Ciao");
    }

    @Override
    public void update(Methods methods)
        {
        methods
            .descriptor(Greet.getDescriptor())
            .unary("Greet", this::greet)
            .unary("SetGreeting", this::setGreeting);
        }

    // ---- service methods -------------------------------------------------
    
    private void greet(Greet.GreetRequest request, StreamObserver<Greet.GreetResponse> observer)
        {
        String name = Optional.ofNullable(request.getName()).orElse("World");
        String msg  = String.format("%s %s!", greeting, name);

        complete(observer, Greet.GreetResponse.newBuilder().setMessage(msg).build());
        }

    private void setGreeting(Greet.SetGreetingRequest request, StreamObserver<Greet.SetGreetingResponse> observer)
        {
        greeting = request.getGreeting();

        complete(observer, Greet.SetGreetingResponse.newBuilder().setGreeting(greeting).build());
        }
    }
