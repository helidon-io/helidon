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

package io.helidon.common.types;

/**
 * Modifiers except for {@link io.helidon.common.types.AccessModifier}.
 */
public enum Modifier {
    /**
     * The {@code abstract} modifier.
     */
    ABSTRACT("abstract"),
    /**
     * The {@code default} modifier.
     */
    DEFAULT("default"),
    /**
     * The {@code static} modifier.
     */
    STATIC("static"),
    /**
     * The {@code sealed} modifier.
     */
    SEALED("sealed"),
    /**
     * The {@code final} modifier.
     */
    FINAL("final"),
    /**
     * The {@code transient} modifier.
     */
    TRANSIENT("transient"),
    /**
     * The {@code volatile} modifier.
     */
    VOLATILE("volatile"),
    /**
     * The {@code synchronized} modifier.
     */
    SYNCHRONIZED("synchronized"),
    /**
     * The {@code native} modifier.
     */
    NATIVE("native");

    private final String modifierName;

    Modifier(String modifierName) {
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
