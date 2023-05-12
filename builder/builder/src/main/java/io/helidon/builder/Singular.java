/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applying this annotation to a {@link Builder}-annotated interface method will cause the generated class to also include
 * additional "add*()" methods. This will only apply, however, if the method is for a {@link java.util.Map}, {@link java.util.List},
 * or {@link java.util.Set}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Singular {

    /**
     * The optional value specified here will determine the singular form of the method name.
     * For instance, if we take a method like this:
     * <pre>{@code
     * @Singlular("pickle")
     * List<Pickle> getPickles();
     * }</pre>
     * an additional generated method named {@code addPickle(Pickle val)} will be placed on the builder of the generated class.
     * <p>This annotation only applies to getter methods that return a Map, List, or Set. If left undefined then the add method
     * will used the default method name, dropping any "s" that might be present at the end of the method name (e.g., pickles ->
     * pickle).
     *
     * @return The singular name to add
     */
    String value() default "";

}
