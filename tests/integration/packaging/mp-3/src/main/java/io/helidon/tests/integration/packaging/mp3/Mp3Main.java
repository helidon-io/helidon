/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.packaging.mp3;

import io.helidon.Main;

/**
 * When using  module-info, we must have a main class in the same module, as we cannot
 * have a module main class in a package that does not belong to this module.
 */
public class Mp3Main {
    /**
     * Main method.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Main.main(args);
    }
}
