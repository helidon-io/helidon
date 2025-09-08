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

import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.Services;

public class Main {

    public static void main(String[] args) {
        LogConfig.configureRuntime();
        var helidonExpertAgent = Services.get(HelidonExpertAgent.class);
        var response =
                helidonExpertAgent
//                        .ask("How do create imperative Helidon HTTP GET resource returning 'Hello World' to every request? And Spring?");
                .ask("How do create JAX-RS Helidon HTTP GET resource returning 'Hello World' to every request?");
        System.out.println(response);
    }
}