/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A type annotated with this annotation will be added to native image with reflection support for all methods
 * and fields (including private).
 * A method or field annotated with this method will be added for reflection.
 * <p>
 * This is an alternative to GraalVM native-image's {@code reflect-config.json} file, specific to Helidon.
 * Processing of this annotation requires {@code io.helidon.integrations.graal:helidon-graal-native-image-extension}
 *  on the classpath when building native image.
 * <p>
 * Constructors annotated with this annotation would only be added for reflection if either a field
 * or a method is annotated as well (this is current limitation of API of native image).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Reflected {
}
