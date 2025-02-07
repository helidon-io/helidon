/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.docs.mp.testing;

import java.util.Map;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.testing.AddConfigBlock;
import io.helidon.microprofile.testing.AddConfigSource;
import io.helidon.microprofile.testing.Configuration;
import io.helidon.microprofile.testing.Socket;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.AddConfig;
import io.helidon.microprofile.testing.AddExtension;
import io.helidon.microprofile.testing.AddJaxRs;
import io.helidon.microprofile.testing.DisableDiscovery;
import io.helidon.microprofile.testing.mocking.MockBean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.mockito.Answers;
import org.mockito.Mockito;

import static io.helidon.common.testing.virtualthreads.PinningRecorder.DEFAULT_THRESHOLD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@SuppressWarnings("ALL")
class TestingSnippets {

    // stub
    class MyBean {
        String greeting() {
            return "";
        }
    }

    // stub
    class MyResource {
    }

    // stub
    @interface HelidonTest {
        boolean resetPerTest() default false;
        long pinningThreshold() default DEFAULT_THRESHOLD;
        boolean pinningDetection() default false;
    }

    // stub
    @interface Test {
    }

    class Snippet1 {

        // tag::snippet_1[]
        @HelidonTest // <1>
        class MyTest {
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @DisableDiscovery // <1>
        @AddBean(MyBean.class) // <2>
        @HelidonTest
        class MyTest {
        }
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @DisableDiscovery
        @AddJaxRs // <1>
        @AddBean(MyResource.class) // <2>
        @HelidonTest
        class MyTest {
        }
        // end::snippet_3[]
    }

    class Snippet4 {

        // tag::snippet_4[]
        @HelidonTest(resetPerTest = true)
        class MyTest {

            @Test
            void testOne() { // <1>
            }

            @Test
            void testTwo() { // <2>
            }
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        @HelidonTest
        class MyTest {

            @Test
            void testOne() { // <1>
            }

            @Test
            @DisableDiscovery
            @AddBean(MyBean.class)
            void testTwo() { // <2>
            }
        }
        // end::snippet_5[]
    }

    class Snippet6 {

        // tag::snippet_6[]
        @Configuration(useExisting = true)
        @HelidonTest
        class MyTest {
        }
        // end::snippet_6[]
    }

    class Snippet7 {

        // tag::snippet_7[]
        @AddConfig(key = "foo", value = "bar")
        @HelidonTest
        class MyTest {
        }
        // end::snippet_7[]
    }

    class Snippet8 {

        // tag::snippet_8[]
        @AddConfigBlock("""
                foo=bar
                bob=alice
                """)
        @HelidonTest
        class MyTest {
        }
        // end::snippet_8[]
    }

    class Snippet9 {

        // tag::snippet_9[]
        @AddConfigBlock(type = "yaml", value = """
                my-test:
                  foo: bar
                  bob: alice
                """)
        @HelidonTest
        class MyTest {
        }
        // end::snippet_9[]
    }

    class Snippet10 {

        // tag::snippet_10[]
        @HelidonTest
        class MyTest {

            @AddConfigSource
            static ConfigSource config() {
                return MpConfigSources.create(Map.of(
                        "foo", "bar",
                        "bob", "alice"));
            }
        }
        // end::snippet_10[]
    }

    class Snippet11 {

        // tag::snippet_11[]
        @Configuration(configSources = {
                "my-test1.yaml",
                "my-test2.yaml"
        })
        @HelidonTest
        class MyTest {
        }
        // end::snippet_11[]
    }

    class Snippet12 {

        // tag::snippet_12[]
        @AddConfigBlock(value = """
                config_ordinal=120
                foo=bar
                """)
        @HelidonTest
        class MyTest {
        }
        // end::snippet_12[]
    }

    class Snippet13 {

        // tag::snippet_13[]
        @HelidonTest
        class MyTest {

            @Inject
            WebTarget target;
        }
        // end::snippet_13[]
    }

    class Snippet14 {

        // tag::snippet_14[]
        @HelidonTest
        class MyTest {

            @Inject
            @Socket("admin")
            WebTarget target;
        }
        // end::snippet_14[]
    }

    class Snippet15 {

        // tag::snippet_15[]
        @HelidonTest
        @DisableDiscovery // <1>
        @AddBean(MyBean.class) // <2>
        @AddExtension(ConfigCdiExtension.class) // <3>
        @AddConfig(key = "app.greeting", value = "TestHello") // <4>
        class MyTest {
            @Inject
            MyBean myBean;

            @Test
            void testGreeting() {
                assertThat(myBean, notNullValue());
                assertThat(myBean.greeting(), is("TestHello"));
            }
        }

        @ApplicationScoped
        class MyBean {

            @ConfigProperty(name = "app.greeting") // <5>
            String greeting;

            String greeting() {
                return greeting;
            }
        }
        // end::snippet_15[]
    }

    class Snippet16 {

        // tag::snippet_16[]
        @HelidonTest
        @DisableDiscovery // <1>
        @AddJaxRs // <2>
        @AddBean(MyResource.class) // <3>
        class MyTest {

            @Inject
            WebTarget target;

            @Test
            void testGet() {
                String greeting = target.path("/greeting")
                        .request().get(String.class);
                assertThat(greeting, is("Hallo!"));
            }
        }

        @Path("/greeting")
        @RequestScoped
        class MyResource {
            @GET
            Response get() {
                return Response.ok("Hallo!").build();
            }
        }
        // end::snippet_16[]
    }

    class Snippet17 {

        // tag::snippet_17[]
        @HelidonTest
        @AddBean(MyResource.class)
        @AddBean(MyService.class)
        class MyTest {

            @MockBean(answer = Answers.CALLS_REAL_METHODS) // <1>
            MyService myService;

            @Inject
            WebTarget target;

            @Test
            void testService() {
                Mockito.when(myService.test()).thenReturn("Mocked"); // <2>
                String response = target.path("/test").request().get(String.class);
                assertThat(response, is("Mocked"));
            }
        }

        @Path("/test")
        class MyResource {

            @Inject
            MyService myService;

            @GET
            String test() {
                return myService.test();
            }
        }

        @ApplicationScoped
        class MyService {

            String test() {
                return "Not Mocked";
            }
        }
        // end::snippet_17[]
    }

    class Snippet18 {

        // tag::snippet_18[]
        @HelidonTest(pinningDetection = true)
        class MyTest {
        }
        // end::snippet_18[]
    }

    class Snippet19 {

        // tag::snippet_19[]
        @HelidonTest(pinningDetection = true, pinningThreshold = 50) // <1>
        class MyTest {
        }
        // end::snippet_19[]
    }
}
