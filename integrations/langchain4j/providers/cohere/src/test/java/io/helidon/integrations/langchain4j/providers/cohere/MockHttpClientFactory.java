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

package io.helidon.integrations.langchain4j.providers.cohere;

import java.util.List;

import io.helidon.common.Weight;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;

@Service.Singleton
@Service.Named("*")
@Weight(85.0D)
public class MockHttpClientFactory implements Service.ServicesFactory<HttpClientBuilder> {
    @Override
    public List<Service.QualifiedInstance<HttpClientBuilder>> services() {
        return List.of(Service.QualifiedInstance.create(new TrackingHttpClientBuilder(),
                                                        Qualifier.createNamed("customHttpClient")));
    }

    static final class TrackingHttpClientBuilder extends JdkHttpClientBuilder {
        private boolean built;

        @Override
        public JdkHttpClient build() {
            built = true;
            return super.build();
        }

        boolean built() {
            return built;
        }
    }
}
