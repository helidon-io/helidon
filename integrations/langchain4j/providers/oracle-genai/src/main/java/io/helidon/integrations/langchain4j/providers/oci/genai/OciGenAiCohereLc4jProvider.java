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

package io.helidon.integrations.langchain4j.providers.oci.genai;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.integrations.langchain4j.AiProvider;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import dev.langchain4j.community.model.oracle.oci.genai.OciGenAiCohereChatModel;
import dev.langchain4j.community.model.oracle.oci.genai.OciGenAiCohereStreamingChatModel;

@AiProvider.ModelConfig(OciGenAiCohereChatModel.class)
@AiProvider.ModelConfig(OciGenAiCohereStreamingChatModel.class)
@Prototype.CustomMethods(OciFactoryMethods.class)
interface OciGenAiCohereLc4jProvider {

    /**
     * OCI LLM Model name or OCID.
     *
     * @return Model name or OCID
     */
    @Option.Configured
    String modelName();

    /**
     * OCI Compartment OCID.
     *
     * @return Compartment OCID
     */
    @Option.Configured
    String compartmentId();

    /**
     * Region is by default set to the current OCI region detected by OCI SDK.
     *
     * @return OCI Region to connect to
     */
    @Option.Configured
    @Option.RegistryService
    Region region();

    /**
     * Authentication provider is by default used from default Service bean found in Service Registry.
     *
     * @return OCI authentication details provider
     */
    @Option.Configured
    @Option.RegistryService
    Optional<BasicAuthenticationDetailsProvider> authProvider();

    /**
     * Custom http client builder.
     *
     * @return the http client builder
     */
    @Option.Configured
    @Option.RegistryService
    Optional<GenerativeAiInferenceClient> genAiClient();
}
