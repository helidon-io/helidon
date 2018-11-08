/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 *
 */
package io.helidon.guides.mp.restfulwebservice;

// tag::mainImports[]
import java.util.concurrent.atomic.AtomicReference;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
// end::mainImports[]
// tag::mpImports[]
import org.eclipse.microprofile.config.inject.ConfigProperty;
// end::mpImports[]

/**
 * Holder for the current greeting message.
 */
@ApplicationScoped
public class GreetingMessage {
    // tag::messageDecl[]
    private final AtomicReference<String> message = new AtomicReference<>();
    // end::messageDecl[]

    /**
     * Create a new greeting message holder, reading the message from configuration.
     *
     * @param message greeting to use
     */
    // tag::ctor[]
    @Inject // <1>
    public GreetingMessage(@ConfigProperty(name = "app.greeting") String message) { // <2>
        this.message.set(message); // <3>
    }
    // end::ctor[]

    // tag::getter[]
    String getMessage() {
        return message.get();
    }
    // end::getter[]

    // tag::setter[]
    void setMessage(String message) {
        this.message.set(message);
    }
    // end::setter[]
}
