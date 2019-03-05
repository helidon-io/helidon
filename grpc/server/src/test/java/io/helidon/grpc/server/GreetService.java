package io.helidon.grpc.server;


import io.grpc.stub.StreamObserver;

import io.helidon.config.Config;

import io.helidon.grpc.server.test.Greet;
import io.helidon.grpc.server.test.Greet.GreetRequest;
import io.helidon.grpc.server.test.Greet.GreetResponse;
import io.helidon.grpc.server.test.Greet.SetGreetingRequest;
import io.helidon.grpc.server.test.Greet.SetGreetingResponse;

import java.util.Optional;
import org.eclipse.microprofile.health.HealthCheckResponse;


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
            .unary("SetGreeting", this::setGreeting)
            .healthCheck(this::healthCheck);
        }

    // ---- service methods -------------------------------------------------
    
    private void greet(GreetRequest request, StreamObserver<GreetResponse> observer)
        {
        String name = Optional.ofNullable(request.getName()).orElse("World");
        String msg  = String.format("%s %s!", greeting, name);

        complete(observer, GreetResponse.newBuilder().setMessage(msg).build());
        }

    private void setGreeting(SetGreetingRequest request, StreamObserver<SetGreetingResponse> observer)
        {
        greeting = request.getGreeting();

        complete(observer, SetGreetingResponse.newBuilder().setGreeting(greeting).build());
        }

    private HealthCheckResponse healthCheck()
        {
        return HealthCheckResponse
                .named(name())
                .up()
                .withData("time", System.currentTimeMillis())
                .withData("greeting", greeting)
                .build();
        }
    }
