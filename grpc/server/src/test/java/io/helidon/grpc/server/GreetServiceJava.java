package io.helidon.grpc.server;


import io.grpc.stub.StreamObserver;

import io.helidon.config.Config;

import java.util.Optional;


/**
 * @author Aleksandar Seovic  2019.02.11
 */
public class GreetServiceJava
        implements GrpcService
    {
    /**
     * The config value for the key {@code greeting}.
     */
    private String greeting;

    GreetServiceJava(Config config) {
        this.greeting = config.get("app.greeting").asString().orElse("Ciao");
    }

    @Override
    public void update(Methods methods)
        {
        methods
            .unary("Greet", this::greet)
            .unary("SetGreeting", this::setGreeting);
        }

    // ---- service methods -------------------------------------------------
    
    private void greet(String name, StreamObserver<String> observer)
        {
        name = Optional.ofNullable(name).orElse("World");
        String msg  = String.format("%s %s!", greeting, name);

        complete(observer, msg);
        }

    private void setGreeting(String greeting, StreamObserver<String> observer)
        {
        this.greeting = greeting;

        complete(observer, greeting);
        }
    }
