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
package io.helidon.common.types;

/**
 * Access modifier of the class model components.
 */
public enum AccessModifier {

    /**
     * Public access modifier.
     */
    PUBLIC("public"),
    /**
     * Protected access modifier.
     */
    PROTECTED("protected"),
    /**
     * Package private modifier.
     */
    PACKAGE_PRIVATE(""),
    /**
     * Private access modifier.
     */
    PRIVATE("private");

    private final String modifierName;

    AccessModifier(String modifierName) {
        this.modifierName = modifierName;
    }

    /**
     * Return access modifier name which should be used in the generated component.
     *
     * @return modifier name
     */
    public String modifierName() {
        return modifierName;
    }

}
