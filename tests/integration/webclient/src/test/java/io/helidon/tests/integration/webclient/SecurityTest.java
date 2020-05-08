/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.webclient;

import javax.json.JsonObject;

import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.security.WebClientSecurity;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for {@link WebClientSecurity}.
 */
public class SecurityTest extends TestParent {

    @Test
    void testBasic() {
        performOperation("/secure/basic");
    }

    @Test
    void testBasicOutbound() {
        //This test tests whether server security is properly propagated to client
        performOperation("/secure/basic/outbound");
    }

    private void performOperation(String path) {
        try {
            webClient.get()
                    .path(path)
                    .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jack")
                    .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "password")
                    .request(JsonObject.class)
                    .thenAccept(jsonObject -> assertThat(jsonObject.getString("message"), is("Hello jack!")))
                    .toCompletableFuture()
                    .get();
        } catch (Exception e) {
            fail(e);
        }
    }

}
