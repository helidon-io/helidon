/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.spi;

/**
 * Implementors of this contract are capable of resetting the state of itself (i.e., clears cache, log entries, etc.).
 */
@FunctionalInterface
public interface Resetable {

    /**
     * Resets the state of the object
     *
     * @param deep true to iterate over any contained objects, to reflect the reset into the retained object
     * @return returns true if the state was changed
     */
    boolean reset(boolean deep);

}
