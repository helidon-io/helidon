/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.unix.domain.socket;

import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.helidon.http.Status;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test UNIX domain socket support (both server and client).
 */
@ServerTest
public class UnixDomainSocketTest {
    private static Path socketPath;

    @SetUpServer
    public static void setUpServer(WebServerConfig.Builder builder) {
        String base = System.getProperty("java.io.tmpdir") + "/helidon-socket";
        String suffix = ".sock";
        Path result = null;

        for (int i = 0; i < 100; i++) {
            String tryit = base + (i == 0 ? "" : String.valueOf(i)) + suffix;
            Path path = Paths.get(tryit);
            if (Files.exists(path)) {
                continue;
            }
            result = path;
            break;
        }

        if (result == null) {
            fail("Failed to find a free UNIX domain socket path. Tried 100 possibilities for " + base + suffix);
        }

        socketPath = result;
        builder.bindAddress(UnixDomainSocketAddress.of(result));
    }

    @SetUpRoute
    public static void setUpRoute(HttpRules rules) {
        rules.get("/test", (req, res) -> res.send("Hello World!"));
    }

    @Test
    public void test() {
        WebClient webClient = WebClient.create();
        var address = UnixDomainSocketAddress.of(socketPath);
        var response = webClient.get()
                .address(address)
                .path("/test")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello World!"));
    }
}
