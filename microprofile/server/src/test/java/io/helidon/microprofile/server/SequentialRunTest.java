/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.server;

import org.junit.jupiter.api.Test;

/**
 * Test running servers in sequence within the same VM.
 */
public class SequentialRunTest {
    @Test
    void testSequentialRun() {
        // sequential must always work, as we do not compete for resources (even on same port)
        Server server1 = Server.builder().port(-1).build();
        server1.start();
        int port = server1.port();
        server1.stop();

        Server server2 = Server.builder().port(port).build();
        server2.start();
        server2.stop();
    }

}
