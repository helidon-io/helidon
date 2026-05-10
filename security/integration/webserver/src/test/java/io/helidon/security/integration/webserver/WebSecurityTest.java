/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.security.integration.webserver;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.http.Http;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.SynchronousProvider;
import io.helidon.webserver.Routing;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link WebSecurity}.
 */
public class WebSecurityTest {
    @Test
    public void testExecutorService() throws TimeoutException, InterruptedException {
        Security security = Security.builder().build();

        AtomicReference<Class> execClassHolder = new AtomicReference<>();

        Routing routing = Routing.builder()
                .register(WebSecurity.create(security))
                .get("/unit_test", (req, res) -> {
                    req.context()
                            .get(SecurityContext.class)
                            .ifPresent(context -> execClassHolder.set(context.executorService().getClass()));
                    req.next();
                })
                .build();

        TestClient.create(routing).path("/unit_test").get();

        Class execClass = execClassHolder.get();

        assertThat(execClass, notNullValue());
        assertThat(execClass, is(not(ForkJoinPool.class)));
    }

    @Test
    public void testSecurityEnvironmentRequestedTarget() throws TimeoutException, InterruptedException {
        Security security = Security.builder().build();
        AtomicReference<String> requestedTarget = new AtomicReference<>();

        Routing routing = Routing.builder()
                .register(WebSecurity.create(security))
                .get("/unit_test", (req, res) -> {
                    req.context()
                            .get(SecurityContext.class)
                            .ifPresent(context -> requestedTarget.set(context.env().requestedTarget()));
                    res.send();
                })
                .build();

        TestClient.create(routing)
                .path("/unit_test")
                .queryParameter("b", "2")
                .queryParameter("a", "1")
                .get();

        assertThat(requestedTarget.get(), is("/unit_test?b=2&a=1"));
    }

    @Test
    public void testNamedAuthenticatorEnablesAuthentication() throws TimeoutException, InterruptedException {
        TrackingAuthenticator defaultAuthenticator = new TrackingAuthenticator(false);
        TrackingAuthenticator namedAuthenticator = new TrackingAuthenticator(true);

        Security security = Security.builder()
                .addAuthenticationProvider(defaultAuthenticator, "default")
                .addAuthenticationProvider(namedAuthenticator, "rejector")
                .build();

        Routing routing = Routing.builder()
                .register(WebSecurity.create(security))
                .get("/unit_test",
                     WebSecurity.enforce().authenticator("rejector"),
                     (req, res) -> res.send("unexpected"))
                .build();

        TestResponse response = TestClient.create(routing).path("/unit_test").get();

        assertThat(response.status(), is(Http.Status.UNAUTHORIZED_401));
        assertThat(defaultAuthenticator.calls.get(), is(0));
        assertThat(namedAuthenticator.calls.get(), is(1));
    }

    private static final class TrackingAuthenticator extends SynchronousProvider implements AuthenticationProvider {
        private final boolean reject;
        private final AtomicInteger calls = new AtomicInteger();

        private TrackingAuthenticator(boolean reject) {
            this.reject = reject;
        }

        @Override
        protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
            calls.incrementAndGet();
            if (reject) {
                return AuthenticationResponse.failed("rejector invoked");
            }
            return AuthenticationResponse.success(Principal.create("default invoked"));
        }
    }
}
