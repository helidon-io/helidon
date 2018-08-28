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

import java.util.EnumSet;
import java.util.Set;

/**
 * Flag driving resolution of providers.
 * If provider returns a FINISH state (either {@link SecurityResponse.SecurityStatus#SUCCESS_FINISH} or {@link
 * SecurityResponse.SecurityStatus#FAILURE_FINISH}, the processing is finished with that status.
 */
public enum CompositeProviderFlag {
    /**
     * Provider may fail, nothing happens if it does.
     */
    MAY_FAIL(EnumSet.of(SecurityResponse.SecurityStatus.SUCCESS,
                        SecurityResponse.SecurityStatus.ABSTAIN,
                        SecurityResponse.SecurityStatus.FAILURE)),
    /**
     * If succeeds, no further providers are called, otherwise same as optional.
     */
    SUFFICIENT(EnumSet.of(SecurityResponse.SecurityStatus.SUCCESS, SecurityResponse.SecurityStatus.ABSTAIN)),
    /**
     * Provider may succeed or abstain. If fails, no further providers are called and request fails.
     */
    OPTIONAL(EnumSet.of(SecurityResponse.SecurityStatus.SUCCESS, SecurityResponse.SecurityStatus.ABSTAIN)),
    /**
     * Provider must succeed if called (if sufficient is before required, required is still ignored).
     */
    REQUIRED(EnumSet.of(SecurityResponse.SecurityStatus.SUCCESS)),
    /**
     * Provider must not be successful for this request (e.g. either it does not apply to this request, or it fails)
     */
    FORBIDDEN(EnumSet.of(SecurityResponse.SecurityStatus.ABSTAIN, SecurityResponse.SecurityStatus.FAILURE)),
    /**
     * Provider must fail.
     */
    MUST_FAIL(EnumSet.of(SecurityResponse.SecurityStatus.FAILURE));

    private final Set<SecurityResponse.SecurityStatus> supportedStates;

    CompositeProviderFlag(Set<SecurityResponse.SecurityStatus> supportedStates) {
        this.supportedStates = supportedStates;
    }

    /**
     * Check whether the status is valid for this flag.
     *
     * @param status status as returned by a provider
     * @return true if the status is supported by this flag and the processing should continue
     */
    public boolean isValid(SecurityResponse.SecurityStatus status) {
        return supportedStates.contains(status);
    }
}
