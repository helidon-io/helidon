package io.helidon.metadata.hjson;

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

public class TestExistingTypes {
    @Test
    void testServiceRegistry() throws IOException {
        JObject object;
        try (InputStream inputStream = resource("/service-registry.json")) {
            assertThat(inputStream, notNullValue());
            object = JValue.read(inputStream)
                    .asObject()
                    .value();
        }

        JObject generated = object.objectValue("generated")
                .orElseThrow(() -> new IllegalStateException("Cannot find 'generated' object under root"));

        assertThat(generated.stringValue("trigger"),
                   optionalValue(is("io.helidon.service.codegen.ServiceRegistryCodegenExtension")));
        assertThat(generated.stringValue("value"),
                   optionalValue(is("io.helidon.service.codegen.ServiceRegistryCodegenExtension")));
        assertThat(generated.stringValue("version"),
                   optionalValue(is("1")));
        assertThat(generated.stringValue("comments"),
                   optionalValue(is("Service descriptors in module unnamed/io.helidon.examples.quickstart.se")));

        List<JObject> services = object.objectArray("services")
                .orElseThrow(() -> new IllegalStateException("Cannot find 'services' object under root"));

        assertThat(services, hasSize(2));

        JObject service = services.get(0);

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
        List<JObject> objects;
        try (InputStream inputStream = resource("/config-metadata.json")) {
            assertThat(inputStream, notNullValue());
            objects = JValue.read(inputStream)
                    .asArray()
                    .getObjects();
        }

        assertThat(objects, hasSize(1));

        JObject module = objects.getFirst();

        assertThat(module.stringValue("module"), optionalValue(is("io.helidon.common.configurable")));
        Optional<List<JObject>> types = module.objectArray("types");
        assertThat(types, optionalPresent());
        List<JObject> typesList = types.get();
        assertThat(typesList, hasSize(5));

        JObject first = typesList.getFirst();
        assertThat(first.stringValue("annotatedType"),
                   optionalValue(is("io.helidon.common.configurable.ResourceConfig")));
        assertThat(first.stringValue("type"),
                   optionalValue(is("io.helidon.common.configurable.Resource")));
        assertThat(first.booleanValue("is"),
                   optionalValue(is(true)));
        assertThat(first.intValue("number"),
                   optionalValue(is(49)));

        List<JObject> optionsList = first.objectArray("options")
                .orElse(List.of());
        assertThat(optionsList, hasSize(9));
        JObject firstOption = optionsList.getFirst();
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
        return TestExistingTypes.class.getResourceAsStream(location);
    }

}
