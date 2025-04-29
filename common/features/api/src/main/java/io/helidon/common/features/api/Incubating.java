/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.features.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for incubating feature modules.
 * Incubating features may be changed including backward incompatible changes in between any version of Helidon.
 * Incubating features are NOT production ready features, and may be removed at discretion of Helidon team.
 *
 * @see Preview - mutualy exclusive with Preview
 * @deprecated use {@link io.helidon.common.features.api.Features.Incubating} instead.
 */
@Deprecated(forRemoval = true, since = "4.3.0")
@Target(ElementType.MODULE)
@Retention(RetentionPolicy.SOURCE)
public @interface Incubating {
}
