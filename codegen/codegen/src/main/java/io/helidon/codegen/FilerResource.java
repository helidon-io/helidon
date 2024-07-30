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

package io.helidon.codegen;

/**
 * A resource from output (such as {@code target/META-INF/helidon}) that can have existing
 * values, and may be replaced with a new value.
 */
public interface FilerResource {
    /**
     * Existing bytes of the resource. Returns empty array, if the resource does not exist or is empty.
     *
     * @return bytes of the resource
     */
    byte[] bytes();

    /**
     * New bytes of the resource.
     *
     * @param newBytes new bytes to {@link #write()} to the resource file
     */
    void bytes(byte[] newBytes);

    /**
     * Writes the new bytes to the output. This operation can only be called once per codegen round.
     */
    void write();
}
