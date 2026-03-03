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
package io.helidon.microprofile.tests.testing.junit5;

import java.net.URI;

import io.helidon.microprofile.testing.AddJaxRs;
import io.helidon.microprofile.testing.Socket;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

@HelidonTest
@DisableDiscovery
@AddBean(TestMethodParams.MyBean.class)
@AddBean(TestMethodParams.MyResource.class)
@AddJaxRs
class TestMethodParams {

    @Test
    void testDefaultQualifierParam(@Default String greeting) {
        assertThat(greeting, is("unqualified"));
    }

    @Test
    void testNamedParam(@Named("test") int numbers) {
        assertThat(numbers, is(123));
    }

    @Test
    void testUriMethodParam(@Socket("@default") URI uri) {
        assertThat(uri, notNullValue());
    }

    @Test
    void testRawUriMethodParam(@Socket("@default") String uri) {
        assertThat(uri, notNullValue());
    }

    @Test
    void testWebTargetDefaultQualifier(@Default WebTarget target) {
        testWebTargetNoQualifier(target);
    }

    @Test
    void testWebTargetNoQualifier(WebTarget target) {
        assertThat(target, notNullValue());
        String response = target.path("/test")
                .request()
                .get(String.class);
        assertThat(response, is("Hello from ResourceClass"));
    }

    @Test
    void testSeContainerNoQualifier(SeContainer seContainer) {
        assertThat(seContainer, notNullValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"one", "two", "three"})
    void testParameterized(String param) {
        assertThat(param, anyOf(is("one"), is("two"), is("three")));
    }

    @Path("/test")
    @ApplicationScoped
    public static class MyResource {

        @GET
        public String get() {
            return "Hello from ResourceClass";
        }
    }

    @ApplicationScoped
    public static class MyBean {

        @Produces
        @Default
        public String unqualified() {
            return "unqualified";
        }

        @Produces
        @Named("test")
        public int named() {
            return 123;
        }
    }
}
