/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.tests.functional.mp.syntheticapp;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import io.helidon.microprofile.cdi.Main;
import io.helidon.microprofile.server.ServerCdiExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link MpSyntheticAppMain}.
 */
class MpSyntheticAppTest {
    private static int port;
    private static WebTarget target;

    @BeforeAll
    static void initClass() {
        Main.main(new String[0]);
        port = CDI.current()
                .getBeanManager()
                .getExtension(ServerCdiExtension.class)
                .port();
        assertThat("Port should be not default", port, not(-1));
        assertThat("Port should be not zero", port, not(0));
        target = ClientBuilder.newClient()
                .target("http://localhost:" + port + "/mp/synthetic");
    }

    @AfterAll
    static void destroyClass() {
        ((SeContainer) CDI.current()).close();
        target = null;
    }

    @Test
    void testDependentResource() {
        test("/dep", "dep-tested");
    }

    @Test
    void testApplicationScopedResource() {
        test("/app", "app-tested");
    }

    @Test
    void testRequesetScopedResource() {
        test("/req", "req-tested");
    }

    @Test
    void testNoCdiScopeResource() {
        test("/path", "path-tested");
    }

    private void test(String path, String expected) {
        String response = target.path(path)
                .request()
                .get()
                .readEntity(String.class);

        assertThat(response, is(expected));
    }
}