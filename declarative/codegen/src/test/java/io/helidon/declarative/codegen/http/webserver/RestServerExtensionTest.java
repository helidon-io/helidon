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

package io.helidon.declarative.codegen.http.webserver;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RestServerExtensionTest {

    @Test
    void testExplicitClassLoader() throws IOException {
        Path tempDir = Files.createTempDirectory("rest-server-extension-test");
        Path servicesFile = tempDir.resolve("META-INF/services/")
                .resolve(HttpParameterCodegenProvider.class.getName());
        Files.createDirectories(servicesFile.getParent());
        Files.writeString(servicesFile,
                          TestHttpParameterCodegenProvider.class.getName() + "\n",
                          StandardCharsets.UTF_8);

        ClassLoader providerLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
                                                        RestServerExtensionTest.class.getClassLoader());
        List<HttpParameterCodegenProvider> explicitProviders = RestServerExtension.loadParamProviders(providerLoader);
        boolean foundTestProvider = explicitProviders.stream().anyMatch(TestHttpParameterCodegenProvider.class::isInstance);
        assertThat(foundTestProvider, is(true));
    }

    public static class TestHttpParameterCodegenProvider implements HttpParameterCodegenProvider {

        @Override
        public boolean codegen(ParameterCodegenContext context) {
            return false;
        }
    }
}
