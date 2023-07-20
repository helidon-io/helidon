package io.helidon.nima.tests.integration.webclient;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class CookieTest {
    private final Http1Client client;

    CookieTest(WebServer webServer) {
        Config config = Config.builder()
                .addSource(ConfigSources.classpath("cookies.yaml"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        client = Http1Client.builder()
                .baseUri("http://localhost:" + webServer.port())
                .config(config.get("client"))
                .build();
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/cookie", CookieTest::getHandler)
                .put("/cookie", CookieTest::putHandler);
    }

    @Test
    @Order(1)
    void testCookieGet() {
        try (Http1ClientResponse response = client.get("/cookie").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
        }
    }

    @Test
    @Order(2)
    void testCookiePut() {
        try (Http1ClientResponse response = client.put("/cookie").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
        }
    }

    private static void getHandler(ServerRequest req, ServerResponse res) {
        Http.HeaderValue cookies = req.headers().get(Http.Header.COOKIE);
        if (cookies.allValues().size() == 2
                && cookies.allValues().contains("flavor3=strawberry")       // in application.yaml
                && cookies.allValues().contains("flavor4=raspberry")) {     // in application.yaml
            res.header(Http.Header.SET_COOKIE, "flavor1=chocolate", "flavor2=vanilla");
            res.status(Http.Status.OK_200).send();
        } else {
            res.status(Http.Status.BAD_REQUEST_400).send();
        }
    }

    private static void putHandler(ServerRequest req, ServerResponse res) {
        Http.HeaderValue cookies = req.headers().get(Http.Header.COOKIE);
        if (cookies.allValues().size() == 4
                && cookies.allValues().contains("flavor1=chocolate")
                && cookies.allValues().contains("flavor2=vanilla")
                && cookies.allValues().contains("flavor3=strawberry")
                && cookies.allValues().contains("flavor4=raspberry")) {
            res.status(Http.Status.OK_200).send();
        } else {
            res.status(Http.Status.BAD_REQUEST_400).send();
        }
    }
}