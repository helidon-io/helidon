/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Interface to define that this class is a class with priority.
 * One of the uses is for services loaded by a ServiceLoader.
 */
@FunctionalInterface
public interface Prioritized {
    /**
     * Priority of this class (maybe because it is defined
     * dynamically, so it cannot be defined by an annotation).
     * If not dynamic, you can use the {@code javax.annotation.Priority}
     * annotation rather then implementing this interface as long as
     * it is supported by the library using this {@code Prioritized}.
     *
     * @return the priority of this service
     */
    int priority();
}
