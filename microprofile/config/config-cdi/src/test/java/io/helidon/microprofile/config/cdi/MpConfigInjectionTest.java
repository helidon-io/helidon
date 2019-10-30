/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.config.cdi;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.helidon.config.test.infra.RestoreSystemPropertiesExt;
import io.helidon.microprofile.config.Converters.Ctor;
import io.helidon.microprofile.config.Converters.Of;
import io.helidon.microprofile.config.Converters.Parse;
import io.helidon.microprofile.config.Converters.ValueOf;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Unit test for MP config injection.
 */
@ExtendWith(RestoreSystemPropertiesExt.class)
class MpConfigInjectionTest {
    private static SeContainer CONTAINER;

    @BeforeAll
    static void initClass() {

        // System properties for injection

        System.setProperty("inject.of", "of");
        System.setProperty("inject.valueOf", "valueOf");
        System.setProperty("inject.parse", "parse");
        System.setProperty("inject.ctor", "ctor");

        // CDI container

        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertThat(initializer, is(notNullValue()));
        CONTAINER = initializer.initialize();
    }

    @AfterAll
    static void destroyClass() {
        CONTAINER.close();
    }

    @Test
    public void testImplicitConversion() {

        Bean bean = CDI.current().select(Bean.class).get();

        assertAll("Implicit conversion injection",
                  () -> assertThat("of", bean.of, is(Of.of("of"))),
                  () -> assertThat("valueOf", bean.valueOf, is(ValueOf.valueOf("valueOf"))),
                  () -> assertThat("parse", bean.parse, is(Parse.parse("parse"))),
                  () -> assertThat("ctor", bean.ctor, is(new Ctor("ctor")))
        );
    }

    @Test
    public void testImplicitConversionSubclass() {

        Bean bean = CDI.current().select(SubBean.class,
                new AnnotationLiteral<Specific>() {
                }).get();

        assertAll("Implicit conversion injection",
                () -> assertThat("of", bean.of, is(Of.of("of"))),
                () -> assertThat("valueOf", bean.valueOf, is(ValueOf.valueOf("valueOf"))),
                () -> assertThat("parse", bean.parse, is(Parse.parse("parse"))),
                () -> assertThat("ctor", bean.ctor, is(new Ctor("ctor")))
        );
    }

    @Dependent
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
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target(TYPE)
    public @interface Specific {
    }

    @Dependent
    @Specific
    public static class SubBean extends Bean {
    }
}
