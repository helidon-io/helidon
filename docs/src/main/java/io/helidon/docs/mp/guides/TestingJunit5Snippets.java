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
package io.helidon.docs.mp.guides;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.WebTarget;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("ALL")
class TestingJunit5Snippets {

    // stub
    class TestBean {
    }

    // stub
    class TestExtension implements Extension {
    }

    class Snippet1 {

        // tag::snippet_1[]
        @HelidonTest
        class GreetTest {
            @Test
            void testDefaultGreeting() {
            }
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @HelidonTest(resetPerTest = true)
        class GreetTest {
            @Test
            void testDefaultGreeting(WebTarget webTarget) {
                validate(webTarget, "/greet", "Hello World!");
            }

            @Test
            @AddConfig(key = "app.greeting", value = "Unite")
            void testConfiguredGreeting(WebTarget webTarget) {
                validate(webTarget, "/greet", "Unite World!");
            }

            private void validate(WebTarget webTarget,
                                  String path,
                                  String expected) {

                JsonObject jsonObject = webTarget.path(path)
                        .request()
                        .get(JsonObject.class);

                String message = jsonObject.getString("message");
                assertThat(message, is("Message in JSON"));
            }
        }
        // end::snippet_2[]
    }

    class Snippet4 {

        // tag::snippet_3[]
        @AddBean(TestBean.class)
        class GreetTest {
        }
        // end::snippet_3[]
    }

    class Snippet5 {

        // tag::snippet_4[]
        @AddBean(value = TestBean.class, scope = Dependent.class)
        class GreetTest {
        }
        // end::snippet_4[]
    }

    class Snippet6 {

        // tag::snippet_5[]
        @AddExtension(TestExtension.class)
        class GreetTest {
        }
        // end::snippet_5[]
    }

    class Snippet7 {

        // tag::snippet_6[]
        @DisableDiscovery
        class GreetTest {
        }
        // end::snippet_6[]
    }

    class Snippet8 {

        // tag::snippet_7[]
        @HelidonTest
        @DisableDiscovery
        @AddExtension(ConfigCdiExtension.class)
        @AddBean(GreetTest.ConfiguredBean.class)
        @AddConfig(key = "test.message", value = "Hello Guide!")
        class GreetTest {
            @Inject
            ConfiguredBean bean;

            @Test
            void testBean() {
                assertThat(bean.message(), is("Hello Guide!"));
            }

            public static class ConfiguredBean {
                @Inject
                @ConfigProperty(name = "test.message")
                private String message;

                String message() {
                    return message;
                }
            }
        }
        // end::snippet_7[]
    }

}
