/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.encryption;

/**
 * This class is moved to {@code helidon-config-mp} module.
 * <p>
 * This module used to {@code requires static io.helidon.config.mp} and provided a service implementation through
 * {@code provides}. Unfortunately this is not compatible - if a service is implemented, the dependency must not be static.
 * To avoid forced dependency on MicroProfile APIs in Helidon SE applications that use JPMS, we had to remove this dependency
 * and service.
 *
 * @deprecated this class is moved to {@code helidon-config-mp} module
 */
@Deprecated(forRemoval = true, since = "4.3.4")
public final class MpEncryptionFilter {
    /**
     * This class is moved to {@code helidon-config-mp} module.
     *
     * @deprecated this class is moved to {@code helidon-config-mp} module
     */
    @Deprecated
    public MpEncryptionFilter() {
    }
}
