/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.webserver.Routing;
import io.helidon.webserver.testsupport.TestClient;

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
}
