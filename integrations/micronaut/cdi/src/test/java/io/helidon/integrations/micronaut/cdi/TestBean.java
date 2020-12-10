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

package io.helidon.integrations.micronaut.cdi;

public interface TestBean {
    @CdiIntercepted
    default String cdiAnnotated() {
        return name() + ".cdiAnnotated";
    }

    @µIntercepted
    default String µAnnotated() {
        return name() + ".µAnnotated";
    }

    @CdiIntercepted
    @µIntercepted
    default String bothAnnotated() {
        return name() + ".bothAnnotated";
    }

    // getClass().getSimpleName() returns the name of CDI proxy
    String name();
}
