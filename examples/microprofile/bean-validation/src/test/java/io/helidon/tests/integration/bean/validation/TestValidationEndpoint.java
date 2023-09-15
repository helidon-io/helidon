/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.bean.validation;

import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * When enabled, endpoints with bean validation should return response with BAD REQUEST status.
 */
@HelidonTest
public class TestValidationEndpoint {

    @Inject
    private WebTarget webTarget;


    /**
     * This Endpoint should always fail with BAD REQUEST, as bean validation fails with Not Null.
     */
    @Test
    public void testValidation() {

        Response.StatusType statusInfo = webTarget
                .path("/valid/email")
                .request()
                .get()
                .getStatusInfo();

        assertThat("Endpoint should return BAD REQUEST", statusInfo.getStatusCode(), is(Response.Status.BAD_REQUEST.getStatusCode()));

    }

    /**
     * This test should always work, since no validation is performed.
     */
    @Test
    public void testNormalUsage(){

        Response.StatusType statusInfo = webTarget
                .path("/valid/e@mail.com")
                .request()
                .get()
                .getStatusInfo();
        assertThat("Endpoint should return OK", statusInfo.getStatusCode(), is(Response.Status.OK.getStatusCode()));

    }
}
