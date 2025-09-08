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

import io.helidon.service.registry.Service;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * Input guardrail that prevents user requests containing references to pineapple (ananas) on pizza.
 * It uses {@link PizzaExpert} to detect such content and fails the request when detected.
 */
@Service.Singleton
@Service.Named("no-ananas-guardrail")
public class NoPizzaOnAnanasInputGuardrail implements InputGuardrail {

    private final PizzaExpert expert;

    /**
     * Constructs a {@code NoPizzaOnAnanasInputGuardrail} with the required {@link PizzaExpert}.
     *
     * @param pizzaExpert the expert used to determine if a request mentions pineapple on pizza
     */
    @Service.Inject
    NoPizzaOnAnanasInputGuardrail(PizzaExpert pizzaExpert) {
        expert = pizzaExpert;
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        if (this.expert.ananasOnPizza(userMessage.singleText())) {
            return this.fatal("Ananas on pizza detected!");
        }
        return this.success();
    }
}
