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
import io.helidon.grpc.examples.common.Greet.GreetRequest;
import io.helidon.grpc.examples.common.Greet.GreetResponse;
import io.helidon.grpc.examples.common.Greet.SetGreetingRequest;
import io.helidon.grpc.examples.common.Greet.SetGreetingResponse;
import io.helidon.grpc.server.GrpcService;
import io.helidon.grpc.server.ServiceDescriptor;

import io.grpc.stub.StreamObserver;
import org.eclipse.microprofile.health.HealthCheckResponse;

import static io.helidon.grpc.core.ResponseHelper.complete;

/**
 * A plain Java implementation of the GreetService.
 */
public class GreetService implements GrpcService {
    /**
     * The config value for the key {@code greeting}.
     */
    private String greeting;

    /**
     * Create a {@link GreetService}.
     *
     * @param config  the service configuration
     */
    public GreetService(Config config) {
        this.greeting = config.get("app.greeting").asString().orElse("Ciao");
    }

    @Override
    public void update(ServiceDescriptor.Rules rules) {
        rules.proto(Greet.getDescriptor())
                .unary("Greet", this::greet)
                .unary("SetGreeting", this::setGreeting)
                .healthCheck(this::healthCheck);
    }

    // ---- service methods -------------------------------------------------

    private void greet(GreetRequest request, StreamObserver<GreetResponse> observer) {
        String name = Optional.ofNullable(request.getName()).orElse("World");
        String msg = String.format("%s %s!", greeting, name);

        complete(observer, GreetResponse.newBuilder().setMessage(msg).build());
    }

    private void setGreeting(SetGreetingRequest request, StreamObserver<SetGreetingResponse> observer) {
        greeting = request.getGreeting();

        complete(observer, SetGreetingResponse.newBuilder().setGreeting(greeting).build());
    }

    private HealthCheckResponse healthCheck() {
        return HealthCheckResponse
                .named(name())
                .up()
                .withData("time", System.currentTimeMillis())
                .withData("greeting", greeting)
                .build();
    }
}
