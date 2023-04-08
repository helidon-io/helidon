package io.helidon.microprofile.telemetry;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/")
public class TestResource {
    @GET
    @WithSpan("custom-name")
    public String getIt() {
        return "Hello!";
    }
}
