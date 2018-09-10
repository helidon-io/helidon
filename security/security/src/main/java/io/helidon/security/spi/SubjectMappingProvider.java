/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.security.spi;

import java.util.concurrent.CompletionStage;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;

/**
 * A provider that maps subject(s) authenticated by an authentication provider
 * to a new subject.
 * This may be replacing the subject, adding roles to the subject etc.
 *
 * Subjects may be a {@link ProviderRequest#getSubject() user subject} or a {@link ProviderRequest#getService() service subject}.
 */
@FunctionalInterface
public interface SubjectMappingProvider extends SecurityProvider {
    /**
     * Map grants from authenticated request (e.g. one or both of {@link ProviderRequest#getSubject()} or
     * {@link ProviderRequest#getService()} returns a non-empty value) to a new authentication response.
     *
     * The provider can change/add/remove grants (such as groups, scopes, permissions) or change the subject to a different
     * one.
     *
     * This method is only invoked after a successful authentication.
     *
     * @param providerRequest  request to get user and service subjects from
     * @param previousResponse response from previous authentication or subject mapping provider
     * @return a new authentication response with updated user and/or service subjects
     */
    CompletionStage<AuthenticationResponse> map(ProviderRequest providerRequest, AuthenticationResponse previousResponse);
}
