/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.example.tls;

import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test of the example.
 */
public class TlsTest {

    private static Client client;
    static {

        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
                public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new java.security.SecureRandom());

            client = ClientBuilder.newBuilder()
                    .sslContext(sslcontext)
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static Server server;

    @BeforeAll
    static void initClass() {
        server = Main.startServer();
        server.start();
    }

    @AfterAll
    static void destroyClass() {
        server.stop();
    }

    @Test
    public void testTls() {
        URI restUri = URI.create("https://localhost:" + server.port() + "/");
        Response res = client.target(restUri).request().get();
        assertThat(res.getStatus(), is(200));
        assertThat(res.readEntity(String.class), is("Hello user!"));
    }

}
