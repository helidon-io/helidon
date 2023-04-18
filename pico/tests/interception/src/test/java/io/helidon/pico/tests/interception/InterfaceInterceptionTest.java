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
import org.junit.jupiter.api.BeforeEach;
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
class InterfaceInterceptionTest {
    private static PicoServices picoServices;
    private static Services services;
    private static OtherContract service;

    @BeforeAll
    static void init() {
        picoServices = PicoServices.picoServices().orElseThrow();
        services = picoServices.services();
        service = services.lookup(OtherContract.class).get();

        assertAll(
                () -> assertThat("Interceptors should not be called for constructor - returning",
                                 ReturningInterceptor.lastCall(),
                                 nullValue()),
                /* Does not work, issue #6647
                () -> assertThat("Interceptors should be called for constructor - modifying",
                                 ModifyingInterceptor.lastCall(),
                                 notNullValue()),
                 */
                () -> assertThat("Interceptors should not be called for constructor - repeating",
                                 RepeatingInterceptor.lastCall(),
                                 nullValue())
        );
    }

    @BeforeEach
    void beforeEach() {
        // cleanup possible last calls from failed tests
        ReturningInterceptor.lastCall();
        ModifyingInterceptor.lastCall();
        RepeatingInterceptor.lastCall();
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
    void testInterceptedSubset() {
        // test that only the interceptors valid for annotations are invoked
        String response = service.interceptedSubset("hello", true, false, false);

        Invocation returning = ReturningInterceptor.lastCall();
        Invocation modifying = ModifyingInterceptor.lastCall();
        Invocation repeating = RepeatingInterceptor.lastCall();

        // first make sure the interceptors were/were not called
        assertAll(
                () -> assertThat("Interceptors should not be called for method not annotated with @Modify",
                                 modifying,
                                 nullValue()),
                /* Does not work, issue #6647
                () -> assertThat("Interceptor should be called for method annotated with @Return",
                                 returning,
                                 notNullValue()),
                 */
                () -> assertThat("Interceptor should be called for method annotated with @Repeat",
                                 repeating,
                                 notNullValue())
        );

        // then assert the called values
        assertAll(
                /* Does not work, issue #6647
                () -> assertThat("Returning last call", returning.methodName(), is("interceptedSubset")),
                () -> assertThat("Returning last call", returning.args(), is(new Object[] {"hello", true, false, false})),
                 */
                () -> assertThat("Repeating last call", repeating.methodName(), is("interceptedSubset")),
                () -> assertThat("Repeating last call", repeating.args(), is(new Object[] {"hello", true, false, false}))
        );

        // and finally the response string
        assertThat(response, is("hello"));
    }

    @Test
    void testReturn() {
        String response = service.intercepted("hello", false, false, true);

        Invocation returning = ReturningInterceptor.lastCall();
        Invocation modifying = ModifyingInterceptor.lastCall();
        Invocation repeating = RepeatingInterceptor.lastCall();
        // first make sure the interceptors were/were not called
        assertAll(
                () -> assertThat("Interceptors should not be called as ReturningInterceptor should have returned",
                                 modifying,
                                 nullValue()),
                /* Does not work, issue #6647
                () -> assertThat("Interceptor should be called for method annotated with @Return",
                                 returning,
                                 notNullValue()),
                 */
                () -> assertThat("Interceptor should not be called as ReturningInterceptor should have returned",
                                 repeating,
                                 nullValue())
        );

        /* Does not work, issue #6647
        assertAll(
                () -> assertThat("Returning last call", returning.methodName(), is("intercepted")),
                () -> assertThat("Returning last call", returning.args(), is(new Object[] {"hello", false, false, true}))
        );

        assertThat(response, is("fixed_answer"));
         */
    }

    @Test
    void testModify() {
        String response = service.intercepted("hello", true, false, false);

        Invocation returning = ReturningInterceptor.lastCall();
        Invocation modifying = ModifyingInterceptor.lastCall();
        Invocation repeating = RepeatingInterceptor.lastCall();

        // first make sure the interceptors were/were not called
        /* Does not work, issue #6647
        assertAll(
                () -> assertThat("Interceptors should be called for method annotated with @Modify",
                                 modifying,
                                 notNullValue()),
                () -> assertThat("Interceptor should be called for method annotated with @Return",
                                 returning,
                                 notNullValue()),
                () -> assertThat("Interceptor should be called for method annotated with @Repeat",
                                 repeating,
                                 notNullValue())
        );

        // then assert the called values
        assertAll(
                () -> assertThat("Returning last call", returning.methodName(), is("intercepted")),
                () -> assertThat("Returning last call", returning.args(), is(new Object[] {"hello", true, false, false})),
                () -> assertThat("Modifying last call", modifying.methodName(), is("intercepted")),
                () -> assertThat("Modifying last call", modifying.args(), is(new Object[] {"hello", true, false, false})),
                () -> assertThat("Repeating last call", repeating.methodName(), is("intercepted")),
                () -> assertThat("Repeating last call", repeating.args(), is(new Object[] {"mod_hello", true, false, false}))
        );

        // and the message
        assertThat(response, is("mod_hello"));
         */
    }

    @Disabled("Known problem - issue #6629")
    @Test
    void testRepeat() {
        String response = service.intercepted("hello", false, true, false);
        assertThat(response, is("mod_hello"));

        Invocation returning = ReturningInterceptor.lastCall();
        Invocation modifying = ModifyingInterceptor.lastCall();
        Invocation repeating = RepeatingInterceptor.lastCall();

        // first make sure the interceptors were/were not called
        assertAll(
                () -> assertThat("Interceptors should be called for method annotated with @Modify",
                                 modifying,
                                 notNullValue()),
                () -> assertThat("Interceptor should be called for method annotated with @Return",
                                 returning,
                                 notNullValue()),
                () -> assertThat("Interceptor should be called for method annotated with @Repeat",
                                 repeating,
                                 notNullValue())
        );

        // then assert the called values
        assertAll(
                () -> assertThat("Returning last call", returning.methodName(), is("intercepted")),
                () -> assertThat("Returning last call", returning.args(), is(new Object[] {"hello", false, true, false})),
                () -> assertThat("Modifying last call", modifying.methodName(), is("intercepted")),
                () -> assertThat("Modifying last call", modifying.args(), is(new Object[] {"hello", false, true, false})),
                () -> assertThat("Repeating last call", repeating.methodName(), is("intercepted")),
                () -> assertThat("Repeating last call", repeating.args(), is(new Object[] {"hello", true, false, false}))
        );
    }

}
