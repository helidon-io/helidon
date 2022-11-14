/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.testsubjects.ext.producer;

import java.util.Objects;

import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.test.utils.JsonUtils;

import jakarta.inject.Named;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.tools.utils.CommonUtils.loadStringFromResource;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link io.helidon.pico.test.extended.resources.MyCompileTimeInheritableTestQualifier} and
 * {@link io.helidon.pico.test.extended.resources.producer.RunLevelResourceProducer}.
 */
public class CustomAnnotationProducerTest {

    @Test
    public void sanity() {
        DefaultQualifierAndValue qualifierAndValue = DefaultQualifierAndValue.builder()
                .typeName(DefaultTypeName.create(Named.class))
                .build();
        assertEquals("{\n"
                             + "    \"qualifierTypeName\" : \"jakarta.inject.Named\",\n"
                             + "    \"value\" : null\n"
                             + "}", JsonUtils.prettyPrintJson(qualifierAndValue));
    }

    @Disabled // TODO:
    @Test
    public void runLevel() {
        assertThatProducedIsExpectedFor("RunLevel_InterceptedImpl");
        assertThatProducedIsExpectedFor("RunLevel_StartupImpl");
        assertThatProducedIsExpectedFor("RunLevel_TestingSingleton");
    }

    @Disabled // TODO:
    @Test
    public void myCompileTimeInheritableTestQualifier() {
        assertThatProducedIsExpectedFor("MyCompileTimeInheritableTestQualifier_TestingSingleton_TestingSingleton");
        assertThatProducedIsExpectedFor("MyCompileTimeInheritableTestQualifier_InterceptedImpl_sayHello");
        assertThatProducedIsExpectedFor("MyCompileTimeInheritableTestQualifier_TestingSingleton_TestingSingleton");
    }

    static void assertThatProducedIsExpectedFor(String resourceName) {
        String producedResourcePath = resourceForPath("produced", resourceName);
        String produced = producedResourceFor(resourceName);
        assertThat(producedResourcePath, produced, is(expectedResourceFor(resourceName)));
    }

    static String producedResourceFor(String jsonResourceName) {
        return resourceFor("produced", jsonResourceName);
    }

    static String expectedResourceFor(String jsonResourceName) {
        return resourceFor("expected", jsonResourceName);
    }

    static String resourceFor(String prefix, String jsonResourceName) {
        String resourcePath = resourceForPath(prefix, jsonResourceName);
        String json = Objects.requireNonNull(loadStringFromResource(resourcePath), resourcePath + " not found");
        return JsonUtils.normalizeJson(json);
    }

    static String resourceForPath(String prefix, String jsonResourceName) {
        return prefix + "/resource/for/" + jsonResourceName + ".json";
    }

}
