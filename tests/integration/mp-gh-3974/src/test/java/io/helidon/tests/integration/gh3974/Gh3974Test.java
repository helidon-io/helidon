/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh3974;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(Gh3974Resource.class)
class Gh3974Test {
    private final WebTarget target;

    @Inject
    Gh3974Test(WebTarget target) {
        this.target = target;
    }

    @Test
    void test404() {
        Response response = target.path("/notthere")
                .request()
                .get();

        assertThat(response.getStatus(), is(404));
        assertThat("Response entity should not be an empty string", response.readEntity(String.class), not(""));
    }

    @Test
    void test404NoEntity() {
        Response response = target.path("/test1")
                .request()
                .get();

        assertThat(response.getStatus(), is(404));
        assertThat(response.readEntity(String.class), is(""));
    }

    @Test
    void test404EmptyEntity() {
        Response response = target.path("/test2")
                .request()
                .get();

        assertThat(response.getStatus(), is(404));
        assertThat(response.readEntity(String.class), is(""));
    }

    @Test
    void test404CustomEntity() {
        Response response = target.path("/test3")
                .request()
                .get();

        assertThat(response.getStatus(), is(404));
        assertThat(response.readEntity(String.class), is("NO"));
    }

    @Test
    void test404Exception() {
        Response response = target.path("/test4")
                .request()
                .get();

        assertThat(response.getStatus(), is(404));
        assertThat(response.readEntity(String.class), is(""));
    }

    @Test
    void test404ExceptionEmptyString() {
        Response response = target.path("/test5")
                .request()
                .get();

        assertThat(response.getStatus(), is(404));
        assertThat(response.readEntity(String.class), is(""));
    }

    @Test
    void test404ExceptionCustomString() {
        Response response = target.path("/test6")
                .request()
                .get();

        assertThat(response.getStatus(), is(404));
        assertThat(response.readEntity(String.class), is("NO"));
    }
}
