/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.runtime;

/**
 * Provides a convenient contract for checking whether the current runtime environment is running on/inside an OCI compute node.
 *
 * @see OciExtension
 */
public interface OciAvailability {

    /**
     * Returns true if the implementation determines it is running on/inside an OCI compute node.
     *
     * @param ociConfig the oci config bean
     * @return true if there running on/inside an OCI compute node
     */
    boolean isRunningOnOci(OciConfig ociConfig);

    /**
     * Will source the config bean from {@link OciExtension#ociConfig()} to make the call to {@link #isRunningOnOci(OciConfig)}.
     *
     * @return true if there running on/inside an OCI compute node
     */
    default boolean isRunningOnOci() {
        return isRunningOnOci(OciExtension.ociConfig());
    }

}
