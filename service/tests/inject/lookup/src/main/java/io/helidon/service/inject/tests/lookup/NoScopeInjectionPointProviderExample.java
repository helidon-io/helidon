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

package io.helidon.service.inject.tests.lookup;

import java.util.Optional;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Injection.InjectionPointProvider;
import io.helidon.service.inject.api.Injection.QualifiedInstance;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;

@Injection.Instance
@NoScopeInjectionPointProviderExample.FirstQuali
@NoScopeInjectionPointProviderExample.SecondQuali
class NoScopeInjectionPointProviderExample implements InjectionPointProvider<ContractNoScope> {
    static final Qualifier FIRST_QUALI = Qualifier.create(NoScopeInjectionPointProviderExample.FirstQuali.class);
    static final Qualifier SECOND_QUALI = Qualifier.create(NoScopeInjectionPointProviderExample.SecondQuali.class);

    @Override
    public Optional<QualifiedInstance<ContractNoScope>> first(Lookup lookup) {
        if (lookup.qualifiers().contains(FIRST_QUALI)) {
            return Optional.of(QualifiedInstance.create(new FirstClass(), FIRST_QUALI));
        }
        if (lookup.qualifiers().contains(SECOND_QUALI)) {
            return Optional.of(QualifiedInstance.create(new SecondClass(), SECOND_QUALI));
        }
        return Optional.empty();
    }

    @Injection.Qualifier
    @interface FirstQuali {
    }

    @Injection.Qualifier
    @interface SecondQuali {
    }

    static class FirstClass implements ContractNoScope {

    }

    static class SecondClass implements ContractNoScope {

    }
}
