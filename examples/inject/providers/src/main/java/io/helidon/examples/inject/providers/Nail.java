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

package io.helidon.examples.inject.providers;

/**
 * Normally, one would need to place {@link io.helidon.inject.service.Injection.Contract} on interfaces. Here, however, we used
 * {@code -Ahelidon.inject.autoAddNonContractInterfaces=true} in the {@code pom.xml} thereby making all interfaces into contracts that
 * can be found via {@link io.helidon.inject.Services#first}.
 */
//@Contract
public interface Nail {

    int id();

}
