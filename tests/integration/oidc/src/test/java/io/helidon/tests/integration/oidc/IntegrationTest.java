package io.helidon.tests.integration.oidc;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.jersey.connector.HelidonProperties;
import io.helidon.microprofile.server.Server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


@Testcontainers
public class IntegrationTest {

    private static final ClientConfig CONFIG = new ClientConfig().property(ClientProperties.FOLLOW_REDIRECTS, Boolean.TRUE)
            .property(HelidonProperties.CONFIG,
                      Config.builder()
                              .disableSystemPropertiesSource()
                              .disableEnvironmentVariablesSource()
                              .build()
                              .get("client"));
    private static final Client CLIENT = ClientBuilder.newClient(CONFIG);

    @Container
    public static KeycloakContainer keycloakContainer = new KeycloakContainer()
            .withRealmImportFiles("/test-realm.json", "/test2-realm.json")
            // this enables KeycloakContainer reuse across tests
            .withReuse(true);

    private static final int PORT = 7777;
    private Server server;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("security.providers.1.oidc.identity-uri",
                           keycloakContainer.getAuthServerUrl() + "realms/test/");
        System.setProperty("security.providers.1.oidc.tenants.0.identity-uri",
                           keycloakContainer.getAuthServerUrl() + "realms/test2/");
        System.setProperty("security.providers.1.oidc.frontend-uri", "http://localhost:" + PORT);
    }

    @BeforeEach
    public void beforeEach() {
        server = Server.builder()
                .port(PORT)
                .addResourceClass(GreetResource.class)
                .build()
                .start();
    }

    @AfterAll
    public static void afterAll() {
        server.stop();
    }

    @Test
    public void testSuccessfulLogin() {
        String formUri;
        WebTarget webserverTarget = CLIENT.target("http://localhost:" + server.port());

        //greet endpoint is protected, and we need to get JWT token out of the Keycloak. We will get redirected to the Keycloak.
        try (Response response = webserverTarget.path("/greet").request().get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            //We need to get form URI out of the HTML
            formUri = getRequestUri(response.readEntity(String.class));
        }

        //Sending authentication to the Keycloak and getting redirected back to the running Helidon app.
        Entity<Form> form = Entity.form(new Form().param("username", "userthree")
                                                .param("password", "12345")
                                                .param("credentialId", ""));
        try (Response response = CLIENT.target(formUri).request().post(form)) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is("{\"message\":\"Hello World!\"}"));
        }

        //next request should have cookie set, and we do not need to authenticate again
        try (Response response = webserverTarget.path("/greet").request().get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is("{\"message\":\"Hello World!\"}"));
        }
    }

    @Test
    public void testMultiTenancy() {
        String formUri;
        WebTarget webserverTarget = CLIENT.target("http://localhost:" + server.port());

        //greet endpoint is protected, and we need to get JWT token out of the Keycloak. We will get redirected to the Keycloak.
        try (Response response = webserverTarget.path("/greet").request().header(Http.Header.HOST, "test11").get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            //We need to get form URI out of the HTML
            formUri = getRequestUri(response.readEntity(String.class));
        }

        //Sending authentication to the Keycloak and getting redirected back to the running Helidon app.
        Entity<Form> form = Entity.form(new Form().param("username", "userthree")
                                                .param("password", "12345")
                                                .param("credentialId", ""));
        try (Response response = CLIENT.target(formUri).request().post(form)) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is("{\"message\":\"Hello World!\"}"));
        }

        //next request should have cookie set, and we do not need to authenticate again
        try (Response response = webserverTarget.path("/greet").request().get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is("{\"message\":\"Hello World!\"}"));
        }
    }

    private String getRequestUri(String html) {
        Document document = Jsoup.parse(html);
        return document.getElementById("kc-form-login").attr("action");
    }

}
