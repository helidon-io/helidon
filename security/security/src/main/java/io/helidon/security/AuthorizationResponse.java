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

package io.helidon.security;

/**
 * Response from an authorization decision.
 * <p>
 * Responses that can be returned based on {@link SecurityStatus}:
 * <ul>
 * <li>Success - request is authorized an may proceed - see {@link #permit()}</li>
 * <li>Failure - request is denied and must not proceed - see {@link #deny()}</li>
 * <li>Abstain - the provider could not decide about this request - see {@link #abstain()}</li>
 * </ul>
 */
public final class AuthorizationResponse extends SecurityResponse {

    private static final AuthorizationResponse PERMIT_RESPONSE = builder().status(SecurityStatus.SUCCESS).build();
    private static final AuthorizationResponse DENY_RESPONSE = builder().status(SecurityStatus.FAILURE).build();
    private static final AuthorizationResponse ABSTAIN_RESPONSE = builder().status(SecurityStatus.ABSTAIN).build();

    private AuthorizationResponse(Builder builder) {
        super(builder);
    }

    /**
     * Permit the request.
     *
     * @return correctly initialized response
     */
    public static AuthorizationResponse permit() {
        return PERMIT_RESPONSE;
    }

    /**
     * Deny the request.
     *
     * @return correctly initialized response
     */
    public static AuthorizationResponse deny() {
        return DENY_RESPONSE;
    }

    /**
     * This provider is not capable of making a decision about the resource (e.g. does not know the resource).
     *
     * @return correctly initialized response
     */
    public static AuthorizationResponse abstain() {
        return ABSTAIN_RESPONSE;
    }

    /**
     * Builder for fully customized authorization response.
     *
     * @return Builder instance ready for configuration
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns true if access to resource was permitted.
     *
     * @return true if permitted, false if denied or abstained.
     */
    public boolean isPermitted() {
        return getStatus().isSuccess();
    }

    /**
     * Builder for custom Authorization responses.
     */
    public static class Builder extends SecurityResponse.SecurityResponseBuilder<Builder, AuthorizationResponse> {
        /**
         * Create a new authorization response based on this builder.
         *
         * @return response based on this builder
         */
        @Override
        public AuthorizationResponse build() {
            return new AuthorizationResponse(this);
        }
    }
}
