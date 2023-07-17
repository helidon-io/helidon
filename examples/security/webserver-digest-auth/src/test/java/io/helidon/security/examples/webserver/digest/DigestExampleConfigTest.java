/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.security.examples.webserver.digest;

import java.net.URI;

import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.WebServerConfig;

/**
 * Unit test for {@link DigestExampleConfigMain}.
 */
@ServerTest
public class DigestExampleConfigTest extends DigestExampleTest {

    DigestExampleConfigTest(Http1Client client, URI uri) {
        super(client, uri);
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder server) {
        DigestExampleConfigMain.setup(server);
    }
}
