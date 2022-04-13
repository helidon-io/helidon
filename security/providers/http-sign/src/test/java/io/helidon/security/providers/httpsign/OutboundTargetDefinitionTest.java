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

package io.helidon.security.providers.httpsign;

import io.helidon.config.Config;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class OutboundTargetDefinitionTest {

    private static final String OUTBOUND_SIGNATURE_KEY = "security.providers.0.http-signatures.outbound.0.signature";

    @Test
    public void testBuilderFromConfig() {
        Config config = Config.create();
        OutboundTargetDefinition.Builder builder =
                OutboundTargetDefinition.builder(config.get(OUTBOUND_SIGNATURE_KEY));
        OutboundTargetDefinition d = builder.build();
        assertThat(d.keyId(), is("rsa-key-12345"));
        assertThat(d.header(), is(HttpSignHeader.SIGNATURE));
        assertThat(d.keyConfig().isPresent(), is(true));
        assertThat(d.signedHeadersConfig(), is(notNullValue()));
    }
}
