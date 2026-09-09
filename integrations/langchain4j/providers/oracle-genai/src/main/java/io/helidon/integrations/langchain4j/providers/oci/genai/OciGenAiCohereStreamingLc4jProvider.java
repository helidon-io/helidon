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

package io.helidon.integrations.langchain4j.providers.oci.genai;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.integrations.langchain4j.AiProvider;

import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceAsyncClient;
import dev.langchain4j.community.model.oracle.oci.genai.OciGenAiCohereStreamingChatModel;

@AiProvider.ModelConfig(value = OciGenAiCohereStreamingChatModel.class,
                        providerKey = "oci-gen-ai-cohere")
@Prototype.CustomMethods(OciFactoryMethods.class)
interface OciGenAiCohereStreamingLc4jProvider extends OciGenAiCohereLc4jProvider {

    /**
     * Custom executor for asynchronous request startup and stream processing.
     * A directly supplied or registry-provided executor is borrowed and remains the responsibility of its owner; the
     * model does not shut it down.
     *
     * @return the executor service
     */
    @Option.Configured
    @Option.RegistryService
    Optional<ExecutorService> executorService();

    /**
     * Custom asynchronous OCI GenAI client.
     * A directly supplied or registry-provided client is borrowed and is not closed by the generated factory.
     *
     * @return the asynchronous OCI GenAI client
     */
    @Option.Configured
    @Option.RegistryService
    Optional<GenerativeAiInferenceAsyncClient> genAiAsyncClient();

    /**
     * Disables automatic model close because close may wait for streaming callbacks that can still use the registry.
     * This also leaves internally created clients open; callers using one must close the model explicitly after all
     * operations complete and before registry shutdown starts.
     *
     * @return always {@code false}
     */
    @Override
    default boolean closeModelOnShutdown() {
        // A streaming callback may use the registry, so registry shutdown cannot safely wait for model close.
        return false;
    }
}
