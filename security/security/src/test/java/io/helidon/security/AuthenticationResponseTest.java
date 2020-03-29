/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
        response.statusCode().ifPresent(it -> fail("Status code should not be present: " + it));
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.user(), is(Optional.empty()));
        assertThat(response.service(), is(Optional.empty()));
        response.description()
                .ifPresentOrElse(it -> assertThat(it, is(message)), () -> fail("Description should have been filled"));
        response.throwable().ifPresent(it -> fail("Throwable should not be filled"));
    }

    @Test
    public void testFailWithException() {
        String message = "aMessage";
        Throwable throwable = new SecurityException("test");

        AuthenticationResponse response = AuthenticationResponse.failed(message, throwable);
        response.statusCode().ifPresent(it -> fail("Status code should not be present: " + it));
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.user(), is(Optional.empty()));
        assertThat(response.service(), is(Optional.empty()));
        response.description()
                .ifPresentOrElse(it -> assertThat(it, is(message)), () -> fail("Description should have been filled"));
        response.throwable()
                .ifPresentOrElse(it -> assertThat(it, sameInstance(throwable)), () -> fail("Throwable should not be filled"));
    }

    @Test
    public void testAbstain() {
        AuthenticationResponse response = AuthenticationResponse.abstain();
        response.statusCode().ifPresent(it -> fail("Status code should not be present: " + it));
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.ABSTAIN));
        assertThat(response.user(), is(Optional.empty()));
        assertThat(response.service(), is(Optional.empty()));
        response.description().ifPresent(it -> fail("Description should not be filled"));
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
        response.statusCode().ifPresent(it -> fail("Status code should not be present: " + it));
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.user(), is(not(Optional.empty())));

        Subject responseSubject = response.user().get();
        assertThat(responseSubject.principal(), sameInstance(myPrincipal));
        if (null != subject) {
            assertThat(responseSubject, sameInstance(subject));
        } else {
            assertThat(responseSubject.principal(), is(myPrincipal));
        }
    }
}
