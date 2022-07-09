/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.grpc.metrics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;

@ApplicationScoped
@Counted
public class HelloWorldResource {

    static final String MESSAGE_SIMPLE_TIMER = "messageSimpleTimer";

    @Inject
    private MetricRegistry metricRegistry;

    // Do not add other metrics annotations to this method!
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String message() {
        metricRegistry.counter("helloCounter").inc();
        return "Hello World";
    }

    @GET
    @SimplyTimed(name = MESSAGE_SIMPLE_TIMER, absolute = true)
    @Path("/withArg/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String messageWithArg(@PathParam("name") String input){
        return "Hello World, " + input;
    }
}
