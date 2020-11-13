/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import org.eclipse.microprofile.metrics.MetricRegistry;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * HelloWorldResource class.
 */
@Path("helloworld")
@RequestScoped
public class HelloWorldResource {

    @Inject
    MetricRegistry metricRegistry;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String message() {
        metricRegistry.counter("helloCounter").inc();
        return "Hello World";
    }

    @PUT
    @Path("withArg")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    public String messageWithArg(String input){
        return "Hello World, " + input;
    }
}
