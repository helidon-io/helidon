package io.helidon.microprofile.tests.benchmark.techempower;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/plaintext")
public class PlaintextResource {
    @GET
    public Response plaintext() {
        return Response.ok("Hello, World!")
                .header("Server", "helidon")
                .header("Content-Type", "text/plain")
                .build();
    }
}
