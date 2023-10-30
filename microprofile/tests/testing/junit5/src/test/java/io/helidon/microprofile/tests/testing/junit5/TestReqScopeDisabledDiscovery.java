package io.helidon.microprofile.tests.testing.junit5;

import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.*;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@HelidonTest
@DisableDiscovery
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)

// JAX-RS Request scope
@AddJaxRs
@AddBean(TestReqScopeDisabledDiscovery.MyController.class)
public class TestReqScopeDisabledDiscovery {
    @Inject
    private WebTarget target;

    @Test
    void testGet() {
        assertEquals("Hallo!", target
                .path("/greeting")
                .request()
                .get(String.class));
    }

    @Path("/greeting")
    @RequestScoped
    public static class MyController {
        @GET
        public Response get() {
            return Response.ok("Hallo!").build();
        }
    }
}