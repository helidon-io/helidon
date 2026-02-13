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

package io.helidon.integrations.langchain4j.tests.agenticmcp;

import io.helidon.integrations.langchain4j.providers.mock.MockChatModel;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@ServerTest
public class CliServiceTest {

    private static final String PROMPT =
            "Give me an example of a Helidon CLI command to create new quickstart project called kec-project.";

    @SetUpRoute
    static void routing(HttpRules rules) {
        SimpleMcpServer.create().routing(rules);
    }

    CliServiceTest(@Service.Named("mock-mcp-test-model") MockChatModel mockChatModel) {
        mockChatModel.activeRules().add(new CliToolCallingRule());
    }

    @Test
    void cliExpertRespondsWithCommand() {
        var response = Services.get(CliService.class).answer(PROMPT);
        assertThat(response, containsString("helidon init"));
        assertThat(response, containsString("4.2.0"));
    }

    @Test
    void sequentialAgentRespondsWithCommand() {
        var response = Services.get(SequentialAgent.class).ask(PROMPT);
        assertThat(response, containsString("helidon init"));
        assertThat(response, containsString("4.2.0"));
    }
}