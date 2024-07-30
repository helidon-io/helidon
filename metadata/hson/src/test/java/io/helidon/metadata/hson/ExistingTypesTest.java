/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.metadata.hson;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

class ExistingTypesTest {
    @Test
    void testServiceRegistry() throws IOException {
        Hson.Object object;
        try (InputStream inputStream = resource("/service-registry.json")) {
            assertThat(inputStream, notNullValue());
            object = Hson.parse(inputStream)
                    .asObject()
                    .value();
        }

        Hson.Object generated = object.objectValue("generated")
                .orElseThrow(() -> new IllegalStateException("Cannot find 'generated' object under root"));

        assertThat(generated.stringValue("trigger"),
                   optionalValue(is("io.helidon.service.codegen.ServiceRegistryCodegenExtension")));
        assertThat(generated.stringValue("value"),
                   optionalValue(is("io.helidon.service.codegen.ServiceRegistryCodegenExtension")));
        assertThat(generated.stringValue("version"),
                   optionalValue(is("1")));
        assertThat(generated.stringValue("comments"),
                   optionalValue(is("Service descriptors in module unnamed/io.helidon.examples.quickstart.se")));

        List<Hson.Object> services = object.objectArray("services")
                .orElseThrow(() -> new IllegalStateException("Cannot find 'services' object under root"));

        assertThat(services, hasSize(2));

        Hson.Object service = services.get(0);

        assertThat(service.doubleValue("version"), optionalValue(is(1d)));
        assertThat(service.stringValue("type"),
                   optionalValue(is("inject")));
        assertThat(service.stringValue("descriptor"),
                   optionalValue(is("io.helidon.examples.quickstart.se.GreetEndpoint__HttpFeature__ServiceDescriptor")));
        assertThat(service.doubleValue("weight"), optionalValue(is(100d)));
        List<String> contracts = service.stringArray("contracts")
                .orElseThrow(() -> new IllegalStateException("Cannot find 'contracts' object under service"));
        assertThat(contracts, hasItems("io.helidon.examples.quickstart.se.GreetEndpoint__HttpFeature",
                                       "io.helidon.webserver.http.HttpFeature",
                                       "io.helidon.webserver.ServerLifecycle",
                                       "java.util.function.Supplier"));

        service = services.get(1);
        assertThat(service.doubleValue("version"), optionalEmpty());
        assertThat(service.stringValue("type"),
                   optionalValue(is("inject")));
        assertThat(service.stringValue("descriptor"),
                   optionalValue(is("io.helidon.examples.quickstart.se.GreetEndpoint__ServiceDescriptor")));
        assertThat(service.doubleValue("weight"), optionalEmpty());
        contracts = service.stringArray("contracts")
                .orElseThrow(() -> new IllegalStateException("Cannot find 'contracts' object under service"));
        assertThat(contracts, hasItems("io.helidon.examples.quickstart.se.GreetEndpoint"));
    }

    @Test
    void testConfigMetadata() throws IOException {
        List<Hson.Object> objects;
        try (InputStream inputStream = resource("/config-metadata.json")) {
            assertThat(inputStream, notNullValue());
            objects = Hson.parse(inputStream)
                    .asArray()
                    .getObjects();
        }

        assertThat(objects, hasSize(1));

        Hson.Object module = objects.getFirst();

        assertThat(module.stringValue("module"), optionalValue(is("io.helidon.common.configurable")));
        Optional<List<Hson.Object>> types = module.objectArray("types");
        assertThat(types, optionalPresent());
        List<Hson.Object> typesList = types.get();
        assertThat(typesList, hasSize(5));

        Hson.Object first = typesList.getFirst();
        assertThat(first.stringValue("annotatedType"),
                   optionalValue(is("io.helidon.common.configurable.ResourceConfig")));
        assertThat(first.stringValue("type"),
                   optionalValue(is("io.helidon.common.configurable.Resource")));
        assertThat(first.booleanValue("is"),
                   optionalValue(is(true)));
        assertThat(first.intValue("number"),
                   optionalValue(is(49)));

        List<Hson.Object> optionsList = first.objectArray("options")
                .orElse(List.of());
        assertThat(optionsList, hasSize(9));
        Hson.Object firstOption = optionsList.getFirst();
        assertThat(firstOption.stringValue("description"),
                   optionalValue(is("Resource is located on filesystem.\n\n Path of the resource")));
        assertThat(firstOption.stringValue("key"),
                   optionalValue(is("path")));
        assertThat(firstOption.stringValue("method"),
                   optionalValue(is("io.helidon.common.configurable.ResourceConfig."
                                            + "Builder#path(java.util.Optional<java.nio.file.Path>)")));
        assertThat(firstOption.stringValue("type"),
                   optionalValue(is("java.nio.file.Path")));
    }

    private InputStream resource(String location) {
        return ExistingTypesTest.class.getResourceAsStream(location);
    }

}
