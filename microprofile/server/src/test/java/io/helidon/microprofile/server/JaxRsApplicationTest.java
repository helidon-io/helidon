/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.server;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.OptionalMatcher.value;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Class JaxRsApplicationTest.
 */
public class JaxRsApplicationTest {

    @ApplicationPath("/foo/bar/")
    static class MyApplication extends Application {
    }

    @Test
    public void testContextRootNormalization() {
        JaxRsApplication app1 = JaxRsApplication.builder().contextRoot("/").build();
        assertThat(app1.contextRoot(), value(is("/")));
        JaxRsApplication app2 = JaxRsApplication.builder().contextRoot("/foo").build();
        assertThat(app2.contextRoot(), value(is("/foo")));
        JaxRsApplication app3 = JaxRsApplication.builder().contextRoot("/foo/").build();
        assertThat(app3.contextRoot(), value(is("/foo")));
        JaxRsApplication app4 = JaxRsApplication.builder().contextRoot("/foo/bar/").build();
        assertThat(app4.contextRoot(), value(is("/foo/bar")));
        JaxRsApplication app5 = JaxRsApplication.builder().application(MyApplication.class).build();
        assertThat(app5.contextRoot(), value(is("/foo/bar")));
    }
}
