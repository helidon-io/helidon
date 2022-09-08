/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.config;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import io.helidon.microprofile.config.Converters.Ctor;
import io.helidon.microprofile.config.Converters.Of;
import io.helidon.microprofile.config.Converters.Parse;
import io.helidon.microprofile.config.Converters.ValueOf;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;

import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Unit test for MP config injection.
 */
@HelidonTest
@AddConfig(key = "inject.of", value = "of")
@AddConfig(key = "inject.valueOf", value = "valueOf")
@AddConfig(key = "inject.parse", value = "parse")
@AddConfig(key = "inject.ctor", value = "ctor")
@AddBean(value = MpConfigInjectionTest.Bean.class, scope = Dependent.class)
@AddBean(value = MpConfigInjectionTest.SubBean.class, scope = Dependent.class)
class MpConfigInjectionTest {
    @Inject
    private Bean bean;

    @Inject
    @ConfigProperties
    private ConfigPropertyNonBean configPropertyNonBean;

    @Inject
    @ConfigProperties
    private ConfigPropertyNonBean configPropertyInternalNonBean;

    @Inject
    @Specific
    private SubBean subBean;

    @Test
    void testConfigPropertiesWithoutBeanDefiningAnnotation() {
        assertThat(configPropertyNonBean.bikes, is(5));
        assertThat(configPropertyNonBean.electric, is(true));
        assertThat(List.of(configPropertyNonBean.colors), contains("blue","white","orange"));
    }

    @Test
    void testConfigPropertiesWithoutBeanDefiningAnnotationInternal() {
        assertThat(configPropertyInternalNonBean.bikes, is(5));
        assertThat(configPropertyInternalNonBean.electric, is(true));
        assertThat(List.of(configPropertyInternalNonBean.colors), contains("blue","white","orange"));
    }

    @Test
    void testInjectMapNoPrefix() {
        Map<String, String> allProperties = bean.allProperties;
        assertAll(
                () -> assertThat(allProperties, hasEntry("inject.of", "of")),
                () -> assertThat(allProperties, hasEntry("inject.valueOf", "valueOf")),
                () -> assertThat(allProperties, hasEntry("inject.parse", "parse")),
                () -> assertThat(allProperties, hasEntry("inject.ctor", "ctor"))
        );
    }

    @Test
    void testInjectMapWithPrefix() {
        Map<String, String> injectProperties = bean.injectProperties;
        assertAll(
                () -> assertThat(injectProperties, hasEntry("of", "of")),
                () -> assertThat(injectProperties, hasEntry("valueOf", "valueOf")),
                () -> assertThat(injectProperties, hasEntry("parse", "parse")),
                () -> assertThat(injectProperties, hasEntry("ctor", "ctor"))
        );
    }

    @Test
    void testImplicitConversion() {
        assertAll("Implicit conversion injection",
                  () -> assertThat("of", bean.of, is(Of.of("of"))),
                  () -> assertThat("valueOf", bean.valueOf, is(ValueOf.valueOf("valueOf"))),
                  () -> assertThat("parse", bean.parse, is(Parse.parse("parse"))),
                  () -> assertThat("ctor", bean.ctor, is(new Ctor("ctor")))
        );
    }

    @Test
    void testImplicitConversionSubclass() {
        assertAll("Implicit conversion injection",
                  () -> assertThat("of", subBean.of, is(Of.of("of"))),
                  () -> assertThat("valueOf", subBean.valueOf, is(ValueOf.valueOf("valueOf"))),
                  () -> assertThat("parse", subBean.parse, is(Parse.parse("parse"))),
                  () -> assertThat("ctor", subBean.ctor, is(new Ctor("ctor")))
        );
    }

    public static class Bean {
        @Inject
        @ConfigProperty(name = "inject.of")
        public Of of;

        @Inject
        @ConfigProperty(name = "inject.valueOf")
        public ValueOf valueOf;

        @Inject
        @ConfigProperty(name = "inject.parse")
        public Parse parse;

        @Inject
        @ConfigProperty(name = "inject.ctor")
        public Ctor ctor;

        @Inject
        @ConfigProperty(name = "")
        public Map<String, String> allProperties;

        @Inject
        @ConfigProperty(name = "inject")
        public Map<String, String> injectProperties;
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target({TYPE, FIELD})
    public @interface Specific {
    }

    @Specific
    public static class SubBean extends Bean {
    }

    @ConfigProperties(prefix="vehicles")
    public static class ConfigPropertyInternalNonBean {
        public @ConfigProperty(name="motor-bikes") int bikes;
        public boolean electric;
        public String[] colors;
    }
}
