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

package io.helidon.config;

import java.util.stream.Collectors;

import io.helidon.common.media.type.MediaTypes;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergedConfigTest {

    @Test
    void string() {
        assertThat(create("foo.bar=test1", "foo.bar=test2").get("foo.bar").asString().get(), is("test1"));
        assertThat(create("foo.bar=test1", "").get("foo.bar").asString().get(), is("test1"));
        assertThat(create("", "foo.bar=test2").get("foo.bar").asString().get(), is("test2"));
    }

    @Test
    void bool() {
        assertThat(create("foo.bar=false", "foo.bar=true").get("foo.bar").asBoolean().get(), is(false));
        assertThat(create("foo.bar=false", "").get("foo.bar").asBoolean().get(), is(false));
        assertThat(create("foo.bar=true", "").get("foo.bar").asBoolean().get(), is(true));
        assertThat(create("", "foo.bar=true").get("foo.bar").asBoolean().get(), is(true));
    }

    @Test
    void mapper() {
        assertThat(create("foo=false", "foo=true").get("foo").as(TestClass1.class).get(), instanceOf(TestClass1.class));
        assertThat(create("foo=true", "foo=false").get("foo").as(TestClass1.class).get().bool, is(true));
        assertTrue(create("foo=true", "").get("foo").as(TestClass1.class).get().bool == true);
        assertThat(create("", "").get("foo").as(TestClass1.class).isEmpty(), is(true));
        assertThat(create("foo.bar=", "").get("foo.bar").asString().get(), is(""));
        assertThat(create("foo=", "").get("foo").asString().get(), is(""));
    }

    @Test
    void override() {
        assertThat(create("foo.bar=", "").get("foo.bar").asString().get(), is(""));
        assertThat(create("foo.bar=", "").get("foo.bar").asString().get(), is(""));
        assertThat(create("foo.bar=test", "").get("foo.bar").asString().get(), is("test"));
        assertThat(create("foo.bar=", "foo.bar=test").get("foo.bar").asString().get(), is(""));
        assertThat(create("", "foo.bar=test").get("foo.bar").asString().get(), is("test"));
    }

    @Test
    void traverse() {
        assertThat(create("foo.bar=test1\nfoo.dar=test2",
                          "foo.bar=test3\nfoo.dar=test4")
                           .get("foo").traverse()
                           .map(c -> c.asString().get())
                           .collect(Collectors.joining(",")), is("test1,test2"));

        assertThat(create("""
                                  foo.bar=test1
                                  foo.dar=test2
                                  """,
                          """
                                  foo.bar=test3
                                  foo.dar=test4
                                  foo.gar=test5
                                  """)
                           .traverse()
                           .map(c -> c.key() + ":" + c.asString().orElse(c.type().name()))
                           .collect(Collectors.joining(",")), is("foo:OBJECT,foo.bar:test1,foo.dar:test2,foo.gar:test5"));
    }

    private Config create(String props1, String props2) {
        var c1 = Config.builder()
                .addSource(ConfigSources.create(props1, MediaTypes.TEXT_PROPERTIES))
                .addMapper(TestClass1.class, config -> new TestClass1().init(config.asBoolean().orElse(null)))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        var c2 = Config.builder()
                .addSource(ConfigSources.create(props2, MediaTypes.TEXT_PROPERTIES))
                .addMapper(TestClass2.class, config -> new TestClass2().init(config.asBoolean().orElse(null)))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        return MergedConfig.create(c1, c2);
    }

    private static class TestClass1 {
        Boolean bool;

        TestClass1 init(Boolean bool) {
            this.bool = bool;
            return this;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TestClass1 other) {
                return this.bool.equals(other.bool);
            }
            return super.equals(obj);
        }
    }

    private static class TestClass2 {
        Boolean bool;

        TestClass2 init(Boolean bool) {
            this.bool = bool;
            return this;
        }
    }
}
