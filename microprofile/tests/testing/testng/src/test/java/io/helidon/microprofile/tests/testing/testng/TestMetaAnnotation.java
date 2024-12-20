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

package io.helidon.microprofile.tests.testing.testng;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import jakarta.inject.Inject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.helidon.microprofile.testing.testng.AddBean;
import io.helidon.microprofile.testing.testng.AddConfig;
import io.helidon.microprofile.testing.testng.AddConfigBlock;
import io.helidon.microprofile.testing.testng.Configuration;
import io.helidon.microprofile.testing.testng.HelidonTest;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.testng.annotations.Test;

@TestMetaAnnotation.MetaAnnotation
// @HelidonTest is still mandatory in the test and it has no effect in the MetaAnnotation
@HelidonTest
class TestMetaAnnotation {

    @Inject
    MyBean bean;

    @Inject
    @ConfigProperty(name = "some.key1")
    private String value1;

    @Inject
    @ConfigProperty(name = "some.key2")
    private String value2;

    @Inject
    @ConfigProperty(name = "some.key")
    private String someKey;

    @Inject
    @ConfigProperty(name = "another.key")
    private String anotherKey;

    @Inject
    @ConfigProperty(name = "second-key")
    private String anotherValue;

    @Test
    void testIt() {
        assertThat(bean.hello(), is("hello"));
        assertThat(value1, is("some.value1"));
        assertThat(value2, is("some.value2"));
        assertThat(someKey, is("some.value"));
        assertThat(anotherKey, is("another.value"));
        assertThat(anotherValue, is("test-custom-config-second-value"));
    }

    @AddBean(MyBean.class)
    @AddConfigBlock("""
            some.key1=some.value1
            some.key2=some.value2
        """)
    @AddConfig(key = "second-key", value = "test-custom-config-second-value")
    @Configuration(configSources = {"testConfigSources.properties", "testConfigSources.yaml"})
    @Retention(RetentionPolicy.RUNTIME)
    static @interface MetaAnnotation {
    }

    static class MyBean {

        String hello() {
            return "hello";
        }
    }
}
