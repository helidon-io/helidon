/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.restclient.connector;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.json.JsonObject;
import javax.net.ssl.SSLContext;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.WebTarget;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.jersey.connector.HelidonProperties;
import io.helidon.microprofile.tests.junit5.Configuration;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.tests.integration.resclient.connector.GreetResourceClient;
import io.helidon.tests.integration.resclient.connector.GreetResourceFilter;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@HelidonTest
@Configuration(configSources = "tls-config.properties")
public class TlsTest {

    @Test
    void testHelloWorld(WebTarget target) {
        Config config = Config.create(ConfigSources.create(Map.of("tls.server.trust-all", "true")));
        Contexts.globalContext().register(GreetResourceFilter.class, target.getUri());

        GreetResourceClient client = RestClientBuilder.newBuilder()
                .baseUri(URI.create("https://localhost:8080"))
                .property(HelidonProperties.CONFIG, config)
                .build(GreetResourceClient.class);

        JsonObject defaultMessage = client.getDefaultMessage();
        assertThat(defaultMessage.toString(), is("{\"message\":\"Hello World!\"}"));
    }

    @Test
    void restClientSslContextPriority(WebTarget target) throws NoSuchAlgorithmException {
        Config config = Config.create(ConfigSources.create(Map.of("tls.server.trust-all", "true")));
        Contexts.globalContext().register(GreetResourceFilter.class, target.getUri());

        GreetResourceClient client = RestClientBuilder.newBuilder()
                .baseUri(URI.create("https://localhost:8080"))
                .property(HelidonProperties.CONFIG, config)
                .sslContext(SSLContext.getDefault())
                .build(GreetResourceClient.class);

        ProcessingException exception = assertThrows(ProcessingException.class, client::getDefaultMessage);
        assertThat(exception.getCause(), instanceOf(ExecutionException.class));
        assertThat(exception.getCause().getMessage(), endsWith("unable to find valid certification path to requested target"));
    }


}
