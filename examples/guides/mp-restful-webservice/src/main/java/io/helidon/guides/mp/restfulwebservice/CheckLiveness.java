/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

// tag::imports[]
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
// end::imports[]

/**
 * Implements the liveness and readiness endpoints to support service
 * health checking.
 */
// tag::classDecl[]
@ApplicationScoped // <1>
@Health // <2>
public class CheckLiveness implements HealthCheck { // <3>
// end::classDecl[]

    // tag::greetingDecl[]
    @Inject // <1>
    private GreetingProvider greeting; // <2>
    // end::greetingDecl[]

    /**
     * Reports the liveness of the greeting resource.
     *
     * @return JAX-RS Response indicating OK or an error with an explanation
     */
    // tag::callMethod[]
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder()
                .name("greetingAlive"); //<1>
        if (greeting == null || greeting.getMessage().trim().length() == 0) { //<2>
            builder.down() //<3>
                   .withData("greeting", "not set or is empty");
        } else {
            builder.up(); //<4>
        }
        return builder.build(); //<5>
    }
    // end::callMethod[]
}
