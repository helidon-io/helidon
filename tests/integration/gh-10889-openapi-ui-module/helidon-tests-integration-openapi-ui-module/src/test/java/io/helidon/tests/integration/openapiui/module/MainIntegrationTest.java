package io.helidon.tests.integration.openapiui.module;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class MainIntegrationTest extends AbstractMainTest {

    private final Http1Client client;

    MainIntegrationTest(Http1Client client) {
        super(client);
        this.client = client;
    }

    @Test
    void testOpenApiUi() {
        try (Http1ClientResponse response = client.get("/openapi")
                .followRedirects(true)
                .accept(MediaTypes.TEXT_HTML)
                .request()) {
            assertThat("OpenApiUi status", response.status(), is(Status.OK_200));
            String openApiDocument = response.entity().as(String.class);
            assertThat("OpenApiUi content", openApiDocument.contains("<div id=\"swagger-ui\"></div>"));
        }
    }
}