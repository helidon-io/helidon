package io.helidon.webserver.tests;

import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.Socket;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
public class CipherSuiteTest {

    private static final Config CONFIG = Config.just(() -> ConfigSources.classpath("cipherSuiteConfig.yaml").build());

    private final WebClient defaultClient;
    private final WebClient invalidClient;

    CipherSuiteTest(@Socket("@default") URI defaultUri, @Socket("invalid") URI invalidUri) {
        this.defaultClient = WebClient.builder()
                .baseUri(defaultUri)
                .tls(tls -> tls.enabledCipherSuites(List.of("TLS_RSA_WITH_AES_128_GCM_SHA256"))
                        .trustAll(true)
                        .endpointIdentificationAlgorithm("NONE"))
                .build();
        this.invalidClient = WebClient.builder()
                .baseUri(invalidUri)
                .tls(tls -> tls.enabledCipherSuites(List.of("TLS_RSA_WITH_AES_128_GCM_SHA256"))
                        .trustAll(true)
                        .endpointIdentificationAlgorithm("NONE"))
                .build();
    }

    private static final String DEFAULT_ENDPOINT = "Allowed cipher: \"TLS_RSA_WITH_AES_128_GCM_SHA256\"";

    @SetUpServer
    static void setupServer(WebServerConfig.Builder server) {
        server.config(CONFIG.get("server"));
    }

    @SetUpRoute
    static void routeSetupDefault(HttpRouting.Builder builder) {
        builder.get("/", (req, res) -> res.send(DEFAULT_ENDPOINT));
    }

    @SetUpRoute("invalid")
    static void routeSetupInvalid(HttpRouting.Builder builder) {
        builder.get("/", (req, res) -> res.send("This endpoint should not be reached!"));
    }

    @Test
    void testDefaultCipherSuite() {
        String entity = defaultClient.get().requestEntity(String.class);
        assertThat(entity, is(DEFAULT_ENDPOINT));
    }

    @Test
    void testInvalidCipherSuite() {
        UncheckedIOException exception = assertThrows(UncheckedIOException.class, () -> invalidClient.get().request());
        assertThat(exception.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(exception.getCause().getMessage(), is("Received fatal alert: handshake_failure"));
    }




//    @Test
//    void testSupportedAlgorithm() {
//        String response = (String)clientOne.get().request(String.class).await(TIMEOUT);
//        MatcherAssert.assertThat(response, CoreMatchers.is("It works!"));
//        response = (String)clientTwo.get().uri("https://localhost:" + webServer.port("second")).request(String.class).await(TIMEOUT);
//        MatcherAssert.assertThat(response, CoreMatchers.is("It works! Second!"));
//    }
//
//    @Test
//    void testUnsupportedAlgorithm() {
//        Throwable cause = ((CompletionException) Assertions.assertThrows(CompletionException.class, () -> {
//            clientOne.get().uri("https://localhost:" + webServer.port("second")).request().await(TIMEOUT);
//        })).getCause();
//        this.checkCause(cause);
//        cause = ((CompletionException)Assertions.assertThrows(CompletionException.class, () -> {
//            clientTwo.get().request().await(TIMEOUT);
//        })).getCause();
//        this.checkCause(cause);
//    }
//
//    private void checkCause(Throwable cause) {
//        if (cause instanceof IllegalStateException ise) {
//            MatcherAssert.assertThat(ise.getMessage(), CoreMatchers.containsString("Connection reset by the host"));
//        } else {
//            MatcherAssert.assertThat(cause, CoreMatchers.instanceOf(SSLHandshakeException.class));
//            MatcherAssert.assertThat(cause.getMessage(), CoreMatchers.is("Received fatal alert: handshake_failure"));
//        }
//
//    }


}
