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

import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.DisableDiscovery;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Answers;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("ALL")
class TestingJunit5Snippets {

    class Snippet1 {

        // tag::snippet_1[]
        @HelidonTest
        class MyTest {

            @Test
            void testOne(WebTarget target) {
            }
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @TestInstance(TestInstance.Lifecycle.PER_METHOD)
        @HelidonTest
        class MyTest {
        }
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @HelidonTest
        @Priority(1) // <3>
        class MyTest {

            @Inject
            WebTarget target;

            MyService myService;

            @BeforeEach
            void initMock() {
                myService = Mockito.mock(MyService.class, Answers.CALLS_REAL_METHODS); // <1>
            }

            @Produces
            @Alternative // <2>
            MyService mockService() {
                return myService;
            }

            @Test
            void testService() {
                Mockito.when(myService.test()).thenReturn("Mocked"); // <4>
                Response response = target.path("/test").request().get();
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
        // end::snippet_3[]
    }

    class Snippet4 {

        class FirstBean {}

        class SecondBean {}

        // tag::snippet_4[]
        @HelidonTest
        @AddBean(FirstBean.class)
        @AddBean(SecondBean.class)
        @DisableDiscovery
        @Target(ElementType.TYPE)
        @Retention(RetentionPolicy.RUNTIME)
        public @interface CustomMetaAnnotation {
        }

        @CustomMetaAnnotation
        class AnnotationOnClass {
        }
        // end::snippet_4[]

        // tag::snippet_5[]
        @Test
        @AddBean(FirstBean.class)
        @AddBean(SecondBean.class)
        @DisableDiscovery
        @Target(ElementType.METHOD)
        @Retention(RetentionPolicy.RUNTIME)
        public @interface MyTestMethod {
        }

        @HelidonTest
        class AnnotationOnMethod {

            @MyTestMethod
            void testOne() {
            }

            @MyTestMethod
            void testTwo() {
            }
        }
        // end::snippet_5[]
    }
}
