/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.resumable;

import java.util.ServiceLoader;

final class ResumableLookup {

    /**
     * Lazy singleton instance, either abstraction over actual CRaC API or no-op if integration module
     * is missing on classpath.
     */
    private static ResumableSupport singleton;

    private ResumableLookup() {
    }

    static ResumableSupport get() {
        if (singleton == null) {
            singleton = ServiceLoader.load(ResumableSupport.class)
                    .findFirst()
                    .orElseGet(NoopResumable::new);
        }
        return singleton;
    }
}
