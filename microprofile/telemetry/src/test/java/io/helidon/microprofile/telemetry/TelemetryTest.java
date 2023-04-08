package io.helidon.microprofile.telemetry;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(TestResource.class)
class TelemetryTest {
    private final WebTarget target;

    @Inject
    TelemetryTest(WebTarget target) {
        this.target = target;
    }

    @Test
    void testIntercepted() {
        String response = target.request()
                .get(String.class);

        assertThat(response, is("Hello!"));
    }
}
