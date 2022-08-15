/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.gh4654;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.server.Server;
import io.helidon.webserver.testsupport.TemporaryFolder;
import io.helidon.webserver.testsupport.TemporaryFolderExtension;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(TemporaryFolderExtension.class)
class Gh4654StaticContentTest {
    private static Client client;
    private TemporaryFolder folder;
    private Server server;
    private WebTarget target;

    @BeforeAll
    static void setupAll() {
        client = ClientBuilder.newClient();
    }

    @AfterAll
    static void cleanupAll() {
        client.close();
        client = null;
    }

    @BeforeEach
    void setup() throws IOException {
        // cannot use @HelidonTest, as the tmp folder extension requires to be run beforeEach

        // root
        Path root = folder.root().toPath();
        Files.writeString(root.resolve("index.html"), "Root Index HTML");
        Files.writeString(root.resolve("foo.txt"), "Foo TXT");
        // css
        Path cssDir = folder.newFolder("css").toPath();
        Files.writeString(cssDir.resolve("a.css"), "A CSS");
        // bar
        Path other = folder.newFolder("other").toPath();
        Files.writeString(other.resolve("index.html"), "Other Index");

        ConfigProviderResolver cpr = ConfigProviderResolver.instance();
        Config config = cpr.getBuilder()
                .withSources(MpConfigSources.create(Map.of(
                        "server.host", "localhost",
                        "server.port", "0",
                        "server.static.path.location", folder.root().getAbsolutePath(),
                        "server.static.path.context", "/static",
                        "server.static.classpath.location", "/static",
                        "server.static.classpath.context", "/static"
                )))
                .build();
        cpr.registerConfig(config, null);

        server = Server.create()
                .start();

        target = client.target("http://localhost:" + server.port() + "/static");

    }

    @AfterEach
    void cleanup() {
        server.stop();
        target = null;
    }

    @ParameterizedTest(name = "\"{0}\" - {2}")
    @CsvSource({
            "/,Root Index HTML,path should serve index.html",
            "/index.html,Root Index HTML,path should serve index.html",
            "/foo.txt,Foo TXT,path should serve foo.txt",
            "/css/a.css,A CSS,path should serve css/a.css",
            "/other,Other Index,path should serve other/index.html",
            "/other/index.html,Other Index,path should serve other/index.html",
            "/classpath,classpath index,classpath should serve index.html",
            "/classpath/index.html,classpath index,classpath should serve index.html"
    })
    void testExists(String path, String expectedContent, String name) {
        Response response = target.path(path)
                .request()
                .get();

        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), is(expectedContent));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "/not-there.txt",
            "/css/not-there.txt",
            "/classpath/not-there.txt"
    })
    void test404(String path) {
        Response response = target.path(path)
                .request()
                .get();

        assertThat(response.getStatus(), is(404));
    }
}
