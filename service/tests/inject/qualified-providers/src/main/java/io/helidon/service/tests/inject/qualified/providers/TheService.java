/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject.qualified.providers;

import io.helidon.service.inject.api.Injection;

@Injection.Singleton
class TheService {
    private final String first;
    private final int second;
    private final QualifiedContract firstContract;
    private final QualifiedContract secondContract;

    @Injection.Inject
    TheService(@FirstQualifier("first") String first,
               @FirstQualifier("second") int second,
               @SecondQualifier("first") QualifiedContract firstContract,
               @SecondQualifier("second") QualifiedContract secondContract) {
        this.first = first;
        this.second = second;
        this.firstContract = firstContract;
        this.secondContract = secondContract;
    }

    String first() {
        return first;
    }

    int second() {
        return second;
    }

    QualifiedContract firstContract() {
        return firstContract;
    }

    QualifiedContract secondContract() {
        return secondContract;
    }
}
