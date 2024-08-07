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
package io.helidon.tests.integration.packaging.mp3;

import java.util.Map;

import io.helidon.http.Status;
import io.helidon.tests.integration.harness.ProcessRunner;
import io.helidon.tests.integration.harness.ProcessRunner.ExecMode;
import io.helidon.tests.integration.harness.ProcessMonitor;
import io.helidon.tests.integration.harness.WaitStrategy;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

abstract class Mp3PackagingTestIT {

    abstract ExecMode execMode();

    void doTestExitOnStarted() {
        try (ProcessMonitor process = process(Map.of("exit.on.started", "!"))
                .await(WaitStrategy.waitForCompletion())) {

            assertThat(process.get().exitValue(), is(0));
        }
    }

    void doTestGreetResource() {
        try (ProcessMonitor process = process(Map.of())
                .await(WaitStrategy.waitForPort())) {

            WebClient client = WebClient.builder()
                    .baseUri("http://localhost:" + process.port())
                    .build();
            ClientResponseTyped<String> response = client.get("/greet/Joe").request(String.class);
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity(), is("{\"message\":\"Hello Joe!\"}"));
        }
    }

    private ProcessMonitor process(Map<String, ?> properties) {
        return ProcessRunner.of(execMode())
                .finalName("helidon-tests-integration-packaging-mp-3")
                .moduleName("io.helidon.tests.integration.packaging.mp.three")
                .mainClass("io.helidon.tests.integration.packaging.mp3.Mp3Main")
                .properties(properties)
                .start();
    }
}
