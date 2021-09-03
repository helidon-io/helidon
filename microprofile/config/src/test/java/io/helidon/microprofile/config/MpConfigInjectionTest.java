/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Qualifier;

import io.helidon.microprofile.config.Converters.Ctor;
import io.helidon.microprofile.config.Converters.Of;
import io.helidon.microprofile.config.Converters.Parse;
import io.helidon.microprofile.config.Converters.ValueOf;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hamcrest.MatcherAssert.assertThat;
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
    @Specific
    private SubBean subBean;

    @Test
    public void testImplicitConversion() {
        assertAll("Implicit conversion injection",
                  () -> assertThat("of", bean.of, is(Of.of("of"))),
                  () -> assertThat("valueOf", bean.valueOf, is(ValueOf.valueOf("valueOf"))),
                  () -> assertThat("parse", bean.parse, is(Parse.parse("parse"))),
                  () -> assertThat("ctor", bean.ctor, is(new Ctor("ctor")))
        );
    }

    @Test
    public void testImplicitConversionSubclass() {
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
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target({TYPE, FIELD})
    public @interface Specific {
    }

    @Specific
    public static class SubBean extends Bean {
    }
}
