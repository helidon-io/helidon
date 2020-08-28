/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 */
package io.helidon.tests.integration.security.gh2297;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * JAX-RS Resource.
 */
@Path("/greet")
public class Resource {
    /**
     * Simple hello world endpoint.
     *
     * @return returns hello
     */
    @GET
    public String hello() {
        return "Hello World";
    }
}
