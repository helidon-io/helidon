package io.helidon.tests.integration.security.abac.policy;

import io.helidon.http.Status;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
public class MethodPolicyTest {

    @Test
    void testPolicyMatchedByMethod(WebTarget target) {
        try (Response response = target.path("/method")
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is("passed"));
        }
    }


    @Test
    void testPolicyNotMatchedByMethod(WebTarget target) {
        try (Response response = target.path("/method")
                .request()
                .post(Entity.text("Test value"))) {
            assertThat(response.getStatus(), is(Status.FORBIDDEN_403.code()));
        }
    }

}
