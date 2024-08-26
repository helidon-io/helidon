package io.helidon.webserver.tests;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class UncheckedIoExceptionTest {
    @SetUpRoute
    public static void routing(HttpRules rules) {
        rules.get("/fail", ((req, res) -> {
            throw new UncheckedIOException("My Exception", new IOException("Outbound client failure"));
        }));
    }

    @Test
    public void testUncheckedIoTreatedAsAnyOther(Http1Client client) {
        var response = client.get("/fail")
                .request(String.class);

        assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
    }
}
