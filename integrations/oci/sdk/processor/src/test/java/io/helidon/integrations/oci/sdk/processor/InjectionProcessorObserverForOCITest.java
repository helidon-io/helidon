/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.processor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.pico.tools.ToolsException;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.streaming.Stream;
import com.oracle.bmc.streaming.StreamAdmin;
import com.oracle.bmc.streaming.StreamAsync;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static io.helidon.common.types.TypeNameDefault.create;
import static io.helidon.common.types.TypeNameDefault.createFromTypeName;
import static io.helidon.integrations.oci.sdk.processor.InjectionProcessorObserverForOCI.shouldProcess;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

class InjectionProcessorObserverForOCITest {

    @Test
    void generatedPicoArtifactsForTypicalOciServices() {
        TypeName ociServiceType = create(ObjectStorage.class);

        TypeName generatedOciServiceClientTypeName = InjectionProcessorObserverForOCI.toGeneratedServiceClientTypeName(ociServiceType);
        assertThat(generatedOciServiceClientTypeName.name(),
                   equalTo("generated." + ociServiceType.name() + "$$Oci$$Client"));

        String serviceClientBody = InjectionProcessorObserverForOCI.toBody(InjectionProcessorObserverForOCI.TAG_TEMPLATE_SERVICE_CLIENT_PROVIDER_NAME,
                                                                           ociServiceType,
                                                                           generatedOciServiceClientTypeName);
        assertThat(serviceClientBody,
                   equalTo(loadStringFromResource("expected/objectstorage$$Oci$$Client._java_")));

        TypeName generatedOciServiceClientBuilderTypeName = InjectionProcessorObserverForOCI.toGeneratedServiceClientBuilderTypeName(ociServiceType);
        assertThat(generatedOciServiceClientBuilderTypeName.name(),
                   equalTo("generated." + ociServiceType.name() + "$$Oci$$ClientBuilder"));

        String serviceClientBuilderBody = InjectionProcessorObserverForOCI.toBody(InjectionProcessorObserverForOCI.TAG_TEMPLATE_SERVICE_CLIENT_BUILDER_PROVIDER_NAME,
                                                                                  ociServiceType,
                                                                                  generatedOciServiceClientTypeName);
        assertThat(serviceClientBuilderBody,
                   equalTo(loadStringFromResource("expected/objectstorage$$Oci$$ClientBuilder._java_")));
    }

    @Test
    void oddballServiceTypeNames() {
        TypeName ociServiceType = create(Stream.class);
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.maybeDot(ociServiceType),
                                 equalTo(""));
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.usesRegion(ociServiceType),
                                 equalTo(false));

        ociServiceType = create(StreamAsync.class);
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.maybeDot(ociServiceType),
                                 equalTo(""));
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.usesRegion(ociServiceType),
                                 equalTo(false));

        ociServiceType = create(StreamAdmin.class);
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.maybeDot(ociServiceType),
                                 equalTo("."));
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.usesRegion(ociServiceType),
                                 equalTo(true));
    }

    @Test
    void testShouldProcess() {
        TypeName typeName = create(ObjectStorage.class);
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.shouldProcess(typeName, null),
                                 is(true));

        typeName = createFromTypeName("com.oracle.bmc.circuitbreaker.OciCircuitBreaker");
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.shouldProcess(typeName, null),
                                 is(false));

        typeName = createFromTypeName("com.oracle.another.Service");
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.shouldProcess(typeName, null),
                                 is(false));

        typeName = createFromTypeName("com.oracle.bmc.Service");
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.shouldProcess(typeName, null),
                                 is(true));

        typeName = createFromTypeName("com.oracle.bmc.ServiceClient");
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.shouldProcess(typeName, null),
                                 is(false));

        typeName = createFromTypeName("com.oracle.bmc.ServiceClientBuilder");
        MatcherAssert.assertThat(InjectionProcessorObserverForOCI.shouldProcess(typeName, null),
                                 is(false));
    }

    @Test
    void loadTypeNameExceptions() {
        Set<String> set = InjectionProcessorObserverForOCI.TYPENAME_EXCEPTIONS.get();
        set.addAll(InjectionProcessorObserverForOCI.splitToSet(" M1,  M2,,, "));
        assertThat(set,
                   containsInAnyOrder("M1",
                                      "M2",
                                      "test1",
                                      "com.oracle.bmc.Region",
                                      "com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider",
                                      "com.oracle.bmc.circuitbreaker.OciCircuitBreaker"
                   ));
    }

    @Test
    void loadNoDotExceptions() {
        Set<String> set = InjectionProcessorObserverForOCI.NO_DOT_EXCEPTIONS.get();
        set.addAll(InjectionProcessorObserverForOCI.splitToSet("Manual1, Manual2 "));
        assertThat(set,
                   containsInAnyOrder("Manual1",
                                      "Manual2",
                                      "test2",
                                      "com.oracle.bmc.streaming.Stream",
                                      "com.oracle.bmc.streaming.StreamAsync"
                   ));
    }

    static String loadStringFromResource(String resourceNamePath) {
        try {
            try (InputStream in = InjectionProcessorObserverForOCITest.class.getClassLoader().getResourceAsStream(resourceNamePath)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            throw new ToolsException("Failed to load: " + resourceNamePath, e);
        }
    }

}
