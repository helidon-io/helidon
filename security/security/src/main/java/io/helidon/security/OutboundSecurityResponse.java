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

import java.util.List;
import java.util.Map;

/**
 * Response of outbound security provider.
 */
public final class OutboundSecurityResponse extends SecurityResponse {
    private static final OutboundSecurityResponse ABSTAIN = OutboundSecurityResponse.builder().status(SecurityStatus.ABSTAIN)
            .build();

    private OutboundSecurityResponse(Builder builder) {
        super(builder);
    }

    /**
     * There is nothing we can add - e.g. we do not propagate identity.
     *
     * @return response with no headers
     */
    public static OutboundSecurityResponse empty() {
        return builder().status(SecurityStatus.SUCCESS).build();
    }

    /**
     * Create a response with these headers. Only needs additional headers (e.g. actual headers sent with
     * request will be existing headers + headers provided here).
     *
     * @param headers Headers to add to request to propagate identity (can also be used to delete headers, if the value list
     *                is empty)
     * @return response correctly initialized
     */
    public static OutboundSecurityResponse withHeaders(Map<String, List<String>> headers) {
        return builder().status(SecurityStatus.SUCCESS).requestHeaders(headers).build();
    }

    /**
     * Get an instance of builder to build custom identity propagation response.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Abstain from processing this request.
     *
     * @return response with abstain status
     */
    public static OutboundSecurityResponse abstain() {
        return ABSTAIN;
    }

    /**
     * Builder class to build custom identity propagation responses.
     */
    public static class Builder extends SecurityResponse.SecurityResponseBuilder<Builder, OutboundSecurityResponse> {
        /**
         * Build identity propagation response based on this builder.
         *
         * @return identity propagation response
         */
        @Override
        public OutboundSecurityResponse build() {
            return new OutboundSecurityResponse(this);
        }
    }
}
