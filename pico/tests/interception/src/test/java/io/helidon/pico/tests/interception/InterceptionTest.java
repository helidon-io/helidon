/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tests.interception;

import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.Services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/*
Order of interceptors:
Returning
Modifying
Repeating
 */
class InterceptionTest {
    private static PicoServices picoServices;
    private static Services services;
    private static TheService service;

    @BeforeAll
    static void init() {
        picoServices = PicoServices.picoServices().orElseThrow();
        services = picoServices.services();
        service = services.lookup(TheService.class).get();

        /*
        // ALL of these assertions fail, as constructor is intercepted even if not annotated
        // existing issue: #6632
        assertAll(
                () -> assertThat("Interceptors should not be called for constructors - returning",
                                 ReturningInterceptor.lastCall(),
                                 nullValue()),
                () -> assertThat("Interceptors should not be called for constructors - modifying",
                                 ModifyingInterceptor.lastCall(),
                                 nullValue()),
                () -> assertThat("Interceptors should not be called for constructors - repeating",
                                 RepeatingInterceptor.lastCall(),
                                 nullValue())
        );
         */
    }

    @Test
    void testNotIntercepted() {
        String response = service.notIntercepted("hello", true, true, true);

        assertAll(
                () -> assertThat("Interceptors should not be called for method not annotated",
                                 ReturningInterceptor.lastCall(),
                                 nullValue()),
                () -> assertThat("Interceptors should not be called for method not annotated",
                                 ModifyingInterceptor.lastCall(),
                                 nullValue()),
                () -> assertThat("Interceptors should not be called for method not annotated",
                                 RepeatingInterceptor.lastCall(),
                                 nullValue())
        );

        assertThat(response, is("hello"));
    }

    @Test
    void testReturn() {
        String response = service.intercepted("hello", false, false, true);

        Invocation last = ReturningInterceptor.lastCall();

        assertAll(
                () -> assertThat("Returning last call", last, notNullValue()),
                () -> assertThat("Returning last call", last.methodName(), is("intercepted")),
                () -> assertThat("Returning last call", last.args(), is(new Object[] {"hello", false, false, true})),
                () -> assertThat("Interceptors should not be called when first interceptor returns",
                                 ModifyingInterceptor.lastCall(),
                                 nullValue()),
                () -> assertThat("Interceptors should not be called when first interceptor returns",
                                 RepeatingInterceptor.lastCall(),
                                 nullValue())
        );

        assertThat(response, is("fixed_answer"));
    }

    @Test
    void testModify() {
        String response = service.intercepted("hello", true, false, false);
        assertThat(response, is("mod_hello"));

        Invocation returning = ReturningInterceptor.lastCall();
        Invocation modifying = ModifyingInterceptor.lastCall();
        Invocation repeating = RepeatingInterceptor.lastCall();

        assertAll(
                () -> assertThat("Returning last call", returning, notNullValue()),
                () -> assertThat("Returning last call", returning.methodName(), is("intercepted")),
                () -> assertThat("Returning last call", returning.args(), is(new Object[] {"hello", true, false, false})),
                () -> assertThat("Modifying last call", modifying, notNullValue()),
                () -> assertThat("Modifying last call", modifying.methodName(), is("intercepted")),
                () -> assertThat("Modifying last call", modifying.args(), is(new Object[] {"hello", true, false, false})),
                () -> assertThat("Repeating last call", repeating, notNullValue()),
                () -> assertThat("Repeating last call", repeating.methodName(), is("intercepted")),
                () -> assertThat("Repeating last call", repeating.args(), is(new Object[] {"mod_hello", true, false, false}))
        );
    }

    @Disabled("Known problem - issue #6629")
    @Test
    void testRepeat() {
        String response = service.intercepted("hello", false, true, false);
        assertThat(response, is("mod_hello"));

        Invocation returning = ReturningInterceptor.lastCall();
        Invocation modifying = ModifyingInterceptor.lastCall();
        Invocation repeating = RepeatingInterceptor.lastCall();

        assertAll(
                () -> assertThat("Returning last call", returning, notNullValue()),
                () -> assertThat("Returning last call", returning.methodName(), is("intercepted")),
                () -> assertThat("Returning last call", returning.args(), is(new Object[] {"hello", false, true, false})),
                () -> assertThat("Modifying last call", modifying, notNullValue()),
                () -> assertThat("Modifying last call", modifying.methodName(), is("intercepted")),
                () -> assertThat("Modifying last call", modifying.args(), is(new Object[] {"hello", false, true, false})),
                () -> assertThat("Repeating last call", repeating, notNullValue()),
                () -> assertThat("Repeating last call", repeating.methodName(), is("intercepted")),
                () -> assertThat("Repeating last call", repeating.args(), is(new Object[] {"hello", true, false, false}))
        );
    }

}
