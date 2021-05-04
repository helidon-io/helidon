/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletionStage;

/**
 * Common methods for security clients.
 *
 * @param <T> type of the response of the security client, such as {@link AuthenticationResponse}
 */
public interface SecurityClient<T extends SecurityResponse> {
    /**
     * Submit the configured fields in the security request and process the security operation (Authentication, Authorization
     * or OutboundSecurity).
     *
     * @return response with information about what happened. Check
     * {@link AuthenticationResponse#status()} to obtain {@link SecurityResponse.SecurityStatus} and easily check if {@link
     * SecurityResponse.SecurityStatus#isSuccess()}
     * Otherwise security request failed or could not be processed.
     */
    CompletionStage<T> submit();

    /**
     * Synchronous complement to {@link #submit()}.
     * Timeout is now hardcoded to 1 minute.
     *
     * @return response of the current security operation
     * @throws SecurityException in case of timeout, interrupted call or exception during future processing
     */
    default T get() {
        return SecurityResponse.get(submit());
    }

}
