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

package io.helidon.examples.inject.interceptors;

import jakarta.inject.Singleton;

@Singleton
class ScrewDriver implements TurningTool {

    @Override
    public String name() {
        return "Screw Driver";
    }

    @Turn
    @Override
    public void turn(String direction) {
        System.out.println(name() + " turning " + direction);
    }

    @Override
    public String toString() {
        return name();
    }

}
