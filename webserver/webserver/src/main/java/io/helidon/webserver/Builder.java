/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

/**
 * This Builder should be implemented by all the builders in order to enable
 * seamless exchange between the builder itself and the objects it's
 * building when calling the methods. Such methods need to have overloads
 * with builder parameters as well as concrete instances.
 *
 * @param <T> the type this builder is building
 */
public interface Builder<T> {

    /**
     * Builds the object.
     *
     * @return the built object
     */
    T build();
}
