/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.functional.requestscope;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class TenantTest {

    @Inject
    private WebTarget baseTarget;

    @Test
    public void test() {
        Response r = baseTarget.path("test")
                .request()
                .get();
        assertThat(r.getStatus(), is(HttpResponseStatus.OK.code()));
    }

    @Test
    public void test2() {
        Response r;
        for (int i = 0; i < 3; i++) {
            r = baseTarget.path("test2")
                    .request()
                    .get();
            assertThat(r.getStatus(), is(HttpResponseStatus.OK.code()));
        }
    }

    @Test
    public void test3() {
        Response r;
        for (int i = 0; i < 3; i++) {
            String paramValue = Integer.toString(i);
            r = baseTarget.path("test3")
                    .queryParam("param1", paramValue)
                    .request()
                    .get();
            assertThat(r.getStatus(), is(HttpResponseStatus.OK.code()));
            String entityValue = r.readEntity(String.class);
            assertThat(entityValue, is(paramValue));
        }
    }

    @Test
    public void test4() {
        Response r;
        r = baseTarget.path("test4")
                .queryParam("param1", "1")
                .request()
                .get();
        assertThat(r.getStatus(), is(HttpResponseStatus.OK.code()));
        String entityValue = r.readEntity(String.class);
        assertThat(entityValue, is("1"));
    }
}
