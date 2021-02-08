/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.cloud.common;

import java.util.Optional;

/**
 * This class to holds the cloudFunction instance obtained from {@link CloudFunctionCdiExtension}.
 *
 */
class CloudFunctionHolder {

    // Instance provided by CloudFunctionCdiExtension
    private final Optional<Object> cloudFunction;

    CloudFunctionHolder(Optional<Object> cloudFunction) {
        this.cloudFunction = cloudFunction;
    }

    /**
     * Returns the cloudFunction provided by {@link CloudFunctionCdiExtension}.
     * @return the cloud function.
     */
    Optional<Object> cloudFunction() {
        return cloudFunction;
    }

}
