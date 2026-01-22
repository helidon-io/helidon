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

package io.helidon.integrations.langchain4j.tests.agentic;

import java.util.Objects;
import java.util.stream.Stream;

import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.guardrail.GuardrailException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testing.Test
public class AgentTest {

    @Test
    void seTest() {
        var helidonExpertAgent = Services.get(HelidonExpertAgent.class);
        var response =
                helidonExpertAgent
                        .ask("How do create imperative Helidon HTTP GET resource returning 'Hello World' to every request?");
        assertThat(response, is("I am SE expert!"));
    }

    @Test
    void mpTest() {
        var helidonExpertAgent = Services.get(HelidonExpertAgent.class);
        var response =
                helidonExpertAgent
                        .ask("How do create JAX-RS Helidon HTTP GET resource returning 'Hello World' to every request?");
        assertThat(response, is("I am MP expert!"));
    }

    @Test
    void mpTestInputGuardRail() {
        var helidonExpertAgent = Services.get(HelidonExpertAgent.class);
        assertGuardrailException(() -> helidonExpertAgent
                .ask("How do create JAX-RS Helidon HTTP GET reactive resource returning 'Hello World' to every request?"));
    }

    @Test
    void mpTestOutputGuardRail() {
        var helidonExpertAgent = Services.get(HelidonExpertAgent.class);
        assertThat(helidonExpertAgent.ask("please retry me req imperative"), is("I am SE expert after retry!"));
    }

    void assertGuardrailException(Executable executable) {
        Throwable ex = assertThrows(AgentInvocationException.class, executable);
        var guardrailRootCause = Stream.iterate(ex, Objects::nonNull, Throwable::getCause)
                .filter(e -> e instanceof GuardrailException)
                .findFirst()
                .map(GuardrailException.class::cast);
        assertTrue(guardrailRootCause.isPresent());
        assertThat(guardrailRootCause.get().getMessage(),
                   containsString("Inappropriate Helidon question! Prompt containing: reactive"));
    }
}
