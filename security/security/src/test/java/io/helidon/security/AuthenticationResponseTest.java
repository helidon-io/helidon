/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.util.Optional;

import io.helidon.common.OptionalHelper;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link AuthenticationResponse}.
 */
public class AuthenticationResponseTest {
    @Test
    public void testFail() {
        String message = "aMessage";
        AuthenticationResponse response = AuthenticationResponse.failed(message);
        response.getStatusCode().ifPresent(it -> fail("Status code should not be present: " + it));
        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.getUser(), is(Optional.empty()));
        assertThat(response.getService(), is(Optional.empty()));
        OptionalHelper.from(response.getDescription())
                .ifPresentOrElse(it -> assertThat(it, is(message)), () -> fail("Description should have been filled"));
        response.getThrowable().ifPresent(it -> fail("Throwable should not be filled"));
    }

    @Test
    public void testFailWithException() {
        String message = "aMessage";
        Throwable throwable = new SecurityException("test");

        AuthenticationResponse response = AuthenticationResponse.failed(message, throwable);
        response.getStatusCode().ifPresent(it -> fail("Status code should not be present: " + it));
        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.getUser(), is(Optional.empty()));
        assertThat(response.getService(), is(Optional.empty()));
        OptionalHelper.from(response.getDescription())
                .ifPresentOrElse(it -> assertThat(it, is(message)), () -> fail("Description should have been filled"));
        OptionalHelper.from(response.getThrowable())
                .ifPresentOrElse(it -> assertThat(it, sameInstance(throwable)), () -> fail("Throwable should not be filled"));
    }

    @Test
    public void testAbstain() {
        AuthenticationResponse response = AuthenticationResponse.abstain();
        response.getStatusCode().ifPresent(it -> fail("Status code should not be present: " + it));
        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.ABSTAIN));
        assertThat(response.getUser(), is(Optional.empty()));
        assertThat(response.getService(), is(Optional.empty()));
        response.getDescription().ifPresent(it -> fail("Description should not be filled"));
    }

    @Test
    public void testSuccessSubject() {
        Principal myPrincipal = Principal.create("aUser");
        Subject subject = Subject.builder().principal(myPrincipal).build();

        AuthenticationResponse response = AuthenticationResponse.success(subject);
        validateSuccessResponse(response, myPrincipal, subject);
    }

    @Test
    public void testSuccessPrincipal() {
        Principal myPrincipal = Principal.create("aUser");

        AuthenticationResponse response = AuthenticationResponse.success(myPrincipal);
        validateSuccessResponse(response, myPrincipal, null);
    }

    private void validateSuccessResponse(AuthenticationResponse response, Principal myPrincipal, Subject subject) {
        response.getStatusCode().ifPresent(it -> fail("Status code should not be present: " + it));
        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.getUser(), is(not(Optional.empty())));

        Subject responseSubject = response.getUser().get();
        assertThat(responseSubject.getPrincipal(), sameInstance(myPrincipal));
        if (null != subject) {
            assertThat(responseSubject, sameInstance(subject));
        } else {
            assertThat(responseSubject.getPrincipal(), is(myPrincipal));
        }
    }
}
