/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.examples.inject.basics.Big;
import io.helidon.examples.inject.basics.Little;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.Qualifier;

import static io.helidon.common.LazyValue.create;

@Injection.Singleton
@Injection.Named("*")
public class BladeProvider implements InjectionPointProvider<Blade> {
    static final LazyValue<Optional<Blade>> LARGE_BLADE = create(() -> Optional.of(new SizedBlade(SizedBlade.Size.LARGE)));
    static final LazyValue<Optional<Blade>> SMALL_BLADE = create(() -> Optional.of(new SizedBlade(SizedBlade.Size.SMALL)));
    private static final Qualifier BIG_QUALIFIER = Qualifier.create(Big.class);
    private static final Qualifier LITTLE_QUALIFIER = Qualifier.create(Little.class);

    /**
     * Here we are creating the right sized blade based upon the injection point's criteria. Note that the scope/cardinality
     * is still (0..1), meaning there will be at most 1 LARGE and at most 1 SMALL blades provided.
     * All {@code Provider}s control the scope of the service instances they provide.
     *
     * @param query the service query
     * @return the blade appropriate for the injection point, or empty if nothing matches
     * @see NailProvider
     */
    @Override
    public Optional<Blade> first(Lookup query) {
        if (contains(query.qualifiers(), Big.class)) {
            return logAndReturn(LARGE_BLADE.get(), query, BIG_QUALIFIER);
        } else if (contains(query.qualifiers(), Little.class)) {
            return logAndReturn(SMALL_BLADE.get(), query, LITTLE_QUALIFIER);
        }
        return logAndReturn(Optional.empty(), query);
    }

    private static Optional<Blade> logAndReturn(
            Optional<Blade> result,
            Lookup query,
            Qualifier... qualifiers) {
        Ip ip = query.injectionPoint().orElse(null);
        // note: a "regular" service lookup via Injection will not have an injection point associated with it
        if (ip != null) {
            System.out.println(ip.service() + "::" + ip.name() + " will be injected with " + result);
        }
        return result;
    }

    private static boolean contains(Collection<Qualifier> qualifiers,
                                    Class<? extends Annotation> anno) {
        return qualifiers.stream().anyMatch(it -> it.typeName().name().equals(anno.getName()));
    }

}
