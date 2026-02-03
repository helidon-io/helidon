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

package io.helidon.builder.tests.third.party.factory;

import java.io.IOException;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metadata.MetadataConstants;
import io.helidon.metadata.hson.Hson;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.fail;

public class LoggerPrototypeTest {
    @Test
    public void testFactoryPrototypeFromConfig() {
        Config config = Config.just(ConfigSources.classpath("/application.yaml"));
        var logger = LoggerConfig.create(config.get("test-1.logger"))
                .build();

        assertThat(logger, notNullValue());
        assertThat(logger.getName(), is("logger.name"));
    }

    @Test
    public void testUsingPrototypeFromConfig() {
        Config config = Config.just(ConfigSources.classpath("/application.yaml"));
        var using = UsingConfig.create(config.get("test-1"));

        assertThat(using, notNullValue());
        assertThat(using.stringOption(), is("string value"));
        assertThat(using.logger().getName(), is("logger.name"));
    }

    @Test
    public void testGeneratedMetadata() throws IOException {
        String configMetaLocation = MetadataConstants.LOCATION + "/" + MetadataConstants.CONFIG_METADATA_FILE;

        try (var stream = LoggerConfigBlueprint.class.getClassLoader()
                .getResourceAsStream(configMetaLocation)) {

            assertThat(configMetaLocation + " file must be generated", stream, notNullValue());

            Hson.Value<?> parsed = Hson.parse(stream);
            // we assume this works, as we test the config metadata generation elsewhere, this is
            // just to test the content is aligned with our prototypes
            var array = parsed.asArray();
            var structs = array.getStructs();
            assertThat("There should be one module only", structs, hasSize(1));
            var module = structs.getFirst();
            assertThat(module.stringValue("module"), optionalValue(is("io.helidon.builder.tests.third.party.factory")));
            var typesOpt = module.arrayValue("types");
            assertThat("We should have \"types\" array", typesOpt, optionalPresent());
            var types = typesOpt.get().getStructs();
            assertThat("There should be two elements in the \"types\" array", types, hasSize(2));

            Hson.Struct loggerStruct = null;
            Hson.Struct usingStruct = null;
            for (Hson.Struct typeStruct : types) {
                var typeOpt = typeStruct.stringValue("type");
                assertThat("Eeach type elmeent must have a \"type\" string value", typeOpt, optionalPresent());
                var type = typeOpt.get();
                switch (type) {
                case "java.lang.System.Logger":
                    loggerStruct = typeStruct;
                    break;
                case "io.helidon.builder.tests.third.party.factory.UsingConfig":
                    usingStruct = typeStruct;
                    break;
                default:
                    fail("Unexpected type in config metadata: " + type);
                }
            }

            assertThat("java.lang.System.Logger type is missing from config metadata",
                       loggerStruct,
                       notNullValue());

            assertThat("\"io.helidon.builder.tests.third.party.factory.UsingConfig\" type is missing from config metadata",
                       usingStruct,
                       notNullValue());

            // we have validated that the two types are present, now we can validate their content
            var annotated = loggerStruct.stringValue("annotatedType");
            assertThat(annotated, optionalValue(is("io.helidon.builder.tests.third.party.factory.LoggerConfig")));
        }
    }
}
