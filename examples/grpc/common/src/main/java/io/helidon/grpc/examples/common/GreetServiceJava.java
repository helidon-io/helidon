/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.grpc.examples.common;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.grpc.server.GrpcService;
import io.helidon.grpc.server.ServiceDescriptor;

import io.grpc.stub.StreamObserver;

import static io.helidon.grpc.core.ResponseHelper.complete;

/**
 * A plain Java implementation of the GreetService.
 */
public class GreetServiceJava
        implements GrpcService {
    /**
     * The config value for the key {@code greeting}.
     */
    private String greeting;

    /**
     * Create a {@link GreetServiceJava}.
     *
     * @param config  the service configuration
     */
    public GreetServiceJava(Config config) {
        this.greeting = config.get("app.greeting").asString().orElse("Ciao");
    }

    @Override
    public void update(ServiceDescriptor.Rules rules) {
        rules.unary("Greet", this::greet)
             .unary("SetGreeting", this::setGreeting);
    }

    // ---- service methods -------------------------------------------------

    private void greet(String name, StreamObserver<String> observer) {
        name = Optional.ofNullable(name).orElse("World");
        String msg = String.format("%s %s!", greeting, name);

        complete(observer, msg);
    }

    private void setGreeting(String greeting, StreamObserver<String> observer) {
        this.greeting = greeting;

        complete(observer, greeting);
    }
}
