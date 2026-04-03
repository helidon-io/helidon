/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tyrus;

import java.util.Set;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.RequestException;
import io.helidon.http.Status;

import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WebSocketUpgraderTest {

    @Test
    void testRejectedHandshakeHeaders() {
        TyrusUpgradeResponse upgradeResponse = new TyrusUpgradeResponse();
        upgradeResponse.setStatus(403);
        upgradeResponse.setReasonPhrase("Forbidden");

        Header authenticate = HeaderValues.create(HeaderNames.WWW_AUTHENTICATE, "Bearer realm=test");
        RequestException exception = TyrusUpgrader.rejectedHandshakeException(upgradeResponse, Set.of(authenticate));

        assertThat(exception.status(), is(Status.FORBIDDEN_403));
        assertThat(exception.responseHeaders().contains(HeaderNames.WWW_AUTHENTICATE), is(true));
        assertThat(exception.responseHeaders().get(HeaderNames.WWW_AUTHENTICATE).get(), is("Bearer realm=test"));
    }
}
