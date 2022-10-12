package io.helidon.examples.quickstart.mp;

import jakarta.ws.rs.GET;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@RegisterRestClient(baseUri = "http://localhost:9999/path")
public interface Client {

    @GET
    String getIt();
}
