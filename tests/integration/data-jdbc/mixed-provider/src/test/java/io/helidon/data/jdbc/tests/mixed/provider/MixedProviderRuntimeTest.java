/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests.mixed.provider;

import java.nio.file.Files;
import java.nio.file.Path;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * End-to-end mixed-provider application validation.
 */
@ServerTest
@SuppressWarnings("helidon:api:preview")
class MixedProviderRuntimeTest {

    private final Http1Client client;

    MixedProviderRuntimeTest(Http1Client client) {
        this.client = client;
    }

    @Test
    void generatedSourcesHonorExplicitProviders() throws Exception {
        Path jpaSource = generatedSource(JpaBookRepository.class, "__Jpa");
        Path jdbcSource = generatedSource(JdbcInventoryRepository.class, "__Jdbc");

        assertThat(Files.isRegularFile(jpaSource), is(true));
        assertThat(Files.isRegularFile(jdbcSource), is(true));
        assertThat(Files.exists(generatedSource(JpaBookRepository.class, "__Jdbc")), is(false));
        assertThat(Files.exists(generatedSource(JdbcInventoryRepository.class, "__Jpa")), is(false));
        assertThat(Files.readString(jpaSource), containsString("@Service.Named(\"mixed\") JpaRepositoryExecutor executor"));
        assertThat(Files.readString(jdbcSource), containsString("@Service.Named(\"mixed\") "
                                                                        + "@Data.ProviderType(\"jdbc\") "
                                                                        + "JdbcClient jdbcClient"));
    }

    @Test
    void invokesJakartaPersistenceRepositoryEndpoint() {
        var response = client.get("/mixed/jpa/1")
                .accept(MediaTypes.APPLICATION_JSON)
                .request(BookResponse.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is(new BookResponse(1, "Helidon in Action")));
    }

    @Test
    void invokesDeclarativeJdbcRepositoryEndpoint() {
        var response = client.get("/mixed/jdbc/declarative/1")
                .accept(MediaTypes.APPLICATION_JSON)
                .request(InventoryResponse.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is(new InventoryResponse("declarative", 1, "guide-stock", 12)));
    }

    @Test
    void invokesImperativeJdbcClientEndpoint() {
        var response = client.get("/mixed/jdbc/imperative/2")
                .accept(MediaTypes.APPLICATION_JSON)
                .request(InventoryResponse.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is(new InventoryResponse("imperative", 2, "reference-stock", 7)));
    }

    private static Path generatedSource(Class<?> repositoryType, String suffix) throws Exception {
        Path testClasses = Path.of(MixedProviderRuntimeTest.class.getProtectionDomain()
                                           .getCodeSource()
                                           .getLocation()
                                           .toURI());
        return testClasses.getParent()
                .resolve("generated-sources/annotations")
                .resolve(repositoryType.getName().replace('.', '/') + suffix + ".java");
    }
}
