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

package io.helidon.codegen;

/**
 * A requires definition of a module-info.java.
 *
 * @param module module that is required
 * @param isTransitive whether the requires is defined as {@code transitive}
 * @param isStatic whether the requires is defined as {@code static}
 */
public record ModuleInfoRequires(String module, boolean isTransitive, boolean isStatic) {
}
