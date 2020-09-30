/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.providers.header;

import io.helidon.security.SubjectType;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.util.TokenHandler;

/**
 * Unit test for {@link HeaderAtnProvider} using builders.
 */
public class HeaderAtnProviderBuilderTest extends HeaderAtnProviderTest {
    @Override
    HeaderAtnProvider getFullProvider() {
        return HeaderAtnProvider.builder()
                .optional(true)
                .authenticate(true)
                .propagate(true)
                .subjectType(SubjectType.USER)
                .atnTokenHandler(TokenHandler.builder()
                                         .tokenHeader("Authorization")
                                         .tokenPrefix("bearer ").build())
                .outboundTokenHandler(TokenHandler.builder()
                                              .tokenHeader("Custom")
                                              .tokenFormat("bearer %1s")
                                              .build())
                .addOutboundTarget(OutboundTarget.builder("localhost")
                                           .addHost("localhost")
                                           .build())
                .build();
    }

    @Override
    HeaderAtnProvider getServiceProvider() {
        return HeaderAtnProvider.builder()
                .subjectType(SubjectType.SERVICE)
                .atnTokenHandler(TokenHandler.builder()
                                         .tokenHeader("Authorization")
                                         .tokenPrefix("bearer ")
                                         .build())
                .addOutboundTarget(OutboundTarget.builder("localhost")
                                           .addHost("localhost")
                                           .build())
                .build();
    }

    @Override
    HeaderAtnProvider getNoSecurityProvider() {
        return HeaderAtnProvider.builder()
                .authenticate(false)
                .propagate(false)
                .build();
    }
}
