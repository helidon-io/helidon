/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.service.tests.interception;

import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.InterceptionException;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
Order of interceptors:
Returning
Modifying
Repeating
 */
@Testing.Test
class DelegatedClassInterceptionTest {
    private static DelegatedClass service;

    static {
        LogConfig.initClass();
    }

    @BeforeAll
    static void init() {
        service = Services.get(DelegatedClass.class);
    }

    @BeforeEach
    void beforeEach() {
        // cleanup possible last calls from failed tests
        ReturningInterceptor.lastCall();
        ModifyingInterceptor.lastCall();
        RepeatingInterceptor.lastCall();
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
                () -> assertThat("Interceptor should be called for method annotated with @Return",
                                 returning,
                                 notNullValue()),
                () -> assertThat("Interceptor should not be called as ReturningInterceptor should have returned",
                                 repeating,
                                 nullValue())
        );

        assertAll(
                () -> assertThat("Returning last call", returning.methodName(), is("intercepted")),
                () -> assertThat("Returning last call", returning.args(), is(new Object[] {"hello", false, false, true}))
        );

        assertThat(response, is("fixed_answer"));
    }

    @Test
    void testModify() {
        String response = service.intercepted("hello", true, false, false);

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
                () -> assertThat("Returning last call", returning.args(), is(new Object[] {"hello", true, false, false})),
                () -> assertThat("Modifying last call", modifying.methodName(), is("intercepted")),
                () -> assertThat("Modifying last call", modifying.args(), is(new Object[] {"hello", true, false, false})),
                () -> assertThat("Repeating last call", repeating.methodName(), is("intercepted")),
                () -> assertThat("Repeating last call", repeating.args(), is(new Object[] {"mod_hello", true, false, false}))
        );

        // and the message
        assertThat(response, is("mod_hello"));
    }

    /**
     * Once the target is called once successfully it should not be allowed to repeat normally.
     */
    @Test
    void testRepeatWithNoExceptionThrownFromTarget() {
        InterceptionException e = assertThrows(InterceptionException.class,
                                               () -> service.intercepted("hello", false, true, false));
        assertThat(e.getMessage(), startsWith("Duplicate invocation, or unknown call type: java.lang.String intercepted"));
        assertThat(e.targetWasCalled(), is(true));
    }

    @Test
    void testRepeatWithExceptionThrownFromTarget() {
        service.throwException(true);

        String response = service.intercepted("hello", false, true, false);
        assertThat(response, equalTo("hello"));

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
                () -> assertThat("Repeating last call", repeating.args(), is(new Object[] {"hello", false, true, false}))
        );
    }

}
