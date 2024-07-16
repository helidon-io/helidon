/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.cdi;

import java.io.IOException;
import java.net.InetAddress;

class Utils {

    static final boolean imdsAvailable() {
        try {
            return InetAddress.getByName(System.getProperty("oci.imds.hostname", "169.254.169.254"))
                    .isReachable(Integer.getInteger("oci.imds.timeout", 100).intValue());
        } catch (final IOException ignored) {
            return false;
        }
    }
}
