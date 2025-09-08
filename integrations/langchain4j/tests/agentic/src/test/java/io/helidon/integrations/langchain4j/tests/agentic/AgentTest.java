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

package io.helidon.integrations.langchain4j.tests.agentic;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

import io.helidon.testing.junit5.Testing;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.guardrail.GuardrailException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class AgentTest {

    @Test
    void medicalTest(ExpertRouterAgent expertRouterAgent) {
        String response = expertRouterAgent.ask("I have a weird rash on my skin, what should I do?");
        assertThat(response, is("You should find a medical attention!"));
    }

    @Test
    void medicalTestGuardrail(ExpertRouterAgent expertRouterAgent) {
        assertGuardrailException(() -> expertRouterAgent.ask(
                "I have a weird rash on my skin, it looks kind of like an ananas on pizza, what should I do?"));
    }

    @Test
    void technicalTest(ExpertRouterAgent expertRouterAgent) {
        String response = expertRouterAgent.ask("How do I construct an owen to make pizza?");
        assertThat(response, is("Get some tools and lets build!"));
    }

    @Test
    void technicalTestGuardrail(ExpertRouterAgent expertRouterAgent) {
        assertGuardrailException(() -> expertRouterAgent.ask("How do I construct an oven to make pizza with ananas?"));
    }

    void assertGuardrailException(Executable executable) {
        Throwable ex = assertThrows(AgentInvocationException.class, executable);
        ex = ex.getCause();
        assertThat(ex, instanceOf(InvocationTargetException.class));
        ex = ex.getCause();
        assertThat(ex, instanceOf(UndeclaredThrowableException.class));
        ex = ex.getCause();
        assertThat(ex, instanceOf(InvocationTargetException.class));
        ex = ex.getCause();
        assertThat(ex, instanceOf(GuardrailException.class));
    }
}
