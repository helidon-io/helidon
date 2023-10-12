/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.examples.microprofile.oci.tls.certificates;

import java.net.URI;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.microprofile.server.Server;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Start the server that will download and watch OCI's Certificates service for dynamic updates.
 */
class SimpleUsageTest {

    private static Client client;
    static {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] arg0, String arg1) {}
                public void checkServerTrusted(X509Certificate[] arg0, String arg1) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new java.security.SecureRandom());

            client = ClientBuilder.newBuilder()
                    .sslContext(sslcontext)
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    // see pom.xml
    public void testIt() {
        assumeTrue(System.getProperty("VAULT_CRYPTO_ENDPOINT") != null,
                               "be sure to set required system properties");
        assumeTrue(System.getProperty("CA_OCID") != null,
                   "be sure to set required system properties");
        assumeTrue(System.getProperty("SERVER_CERT_OCID") != null,
                   "be sure to set required system properties");
        assumeTrue(System.getProperty("SERVER_KEY_OCID") != null,
                   "be sure to set required system properties");

        Server server = Main.startServer();
        try {
            URI restUri = URI.create("https://localhost:" + server.port() + "/");
            try (Response res = client.target(restUri).request().get()) {
                assertThat(res.getStatus(), is(200));
                assertThat(res.readEntity(String.class), is("Hello user!"));
            }
        } finally {
            server.stop();
        }
    }

}
