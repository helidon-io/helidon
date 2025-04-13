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

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import io.helidon.common.Weight;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;

@Service.Singleton
@Service.Named("*")
@Weight(85.0D)
class MockAuthProviderFactory implements Service.ServicesFactory<BasicAuthenticationDetailsProvider> {

    @Override
    public List<Service.QualifiedInstance<BasicAuthenticationDetailsProvider>> services() {
        return Stream.of("mockNamed1", "mockNamed2", "@default")
                .map(n -> Service.QualifiedInstance.create(
                        (BasicAuthenticationDetailsProvider) new MockAuthProvider(n),
                        Qualifier.createNamed(n))).toList();
    }

    @Service.Singleton
    public static class MockAuthProvider implements BasicAuthenticationDetailsProvider {

        private final String name;

        public MockAuthProvider() {
            this.name = "mockDefault";
        }

        private MockAuthProvider(String name) {
            this.name = name;
        }

        @Override
        public String getKeyId() {
            return name;
        }

        @Override
        public InputStream getPrivateKey() {
            return null;
        }

        @Override
        public String getPassPhrase() {
            return name;
        }

        @Override
        public char[] getPassphraseCharacters() {
            return name.toCharArray();
        }
    }

}
