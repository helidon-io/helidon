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

import java.util.List;

import io.helidon.common.Weight;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import org.mockito.Mockito;

@Service.Singleton
@Service.Named("*")
@Weight(85.0D)
public class MockGenAiClientFactory implements Service.ServicesFactory<GenerativeAiInferenceClient> {

    @Override
    public List<Service.QualifiedInstance<GenerativeAiInferenceClient>> services() {
        var genAiClientMock = Mockito.mock(GenerativeAiInferenceClient.class);
        return List.of(Service.QualifiedInstance.create(genAiClientMock, Qualifier.createNamed("@default")));
    }

}
