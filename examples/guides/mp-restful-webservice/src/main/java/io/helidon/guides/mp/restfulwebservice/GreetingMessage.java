/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
