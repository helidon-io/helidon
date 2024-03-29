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

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.examples.inject.basics.Big;
import io.helidon.examples.inject.basics.Little;
import io.helidon.inject.api.ContextualServiceQuery;
import io.helidon.inject.api.InjectionPointInfo;
import io.helidon.inject.api.InjectionPointProvider;
import io.helidon.inject.api.Qualifier;
import io.helidon.inject.api.ServiceInfoCriteria;

import jakarta.inject.Singleton;

import static io.helidon.common.LazyValue.create;

@Singleton
public class BladeProvider implements InjectionPointProvider<Blade> {

    static final LazyValue<Optional<Blade>> LARGE_BLADE = create(() -> Optional.of(new SizedBlade(SizedBlade.Size.LARGE)));
    static final LazyValue<Optional<Blade>> SMALL_BLADE = create(() -> Optional.of(new SizedBlade(SizedBlade.Size.SMALL)));

    /**
     * Here we are creating the right sized blade based upon the injection point's criteria. Note that the scope/cardinality
     * is still (0..1), meaning there will be at most 1 LARGE and at most 1 SMALL blades provided.
     * All {@code Provider}s control the scope of the service instances they provide.
     *
     * @param query the service query
     * @return the blade appropriate for the injection point, or empty if nothing matches
     *
     * @see NailProvider
     */
    @Override
    public Optional<Blade> first(ContextualServiceQuery query) {
        ServiceInfoCriteria criteria = query.serviceInfoCriteria();
        if (contains(criteria.qualifiers(), Big.class)) {
            return logAndReturn(LARGE_BLADE.get(), query);
        } else if (contains(criteria.qualifiers(), Little.class)) {
            return logAndReturn(SMALL_BLADE.get(), query);
        }
        return logAndReturn(Optional.empty(), query);
    }

    static Optional<Blade> logAndReturn(Optional<Blade> result,
                                        ContextualServiceQuery query) {
        InjectionPointInfo ip = query.injectionPointInfo().orElse(null);
        // note: a "regular" service lookup via Injection will not have an injection point associated with it
        if (ip != null) {
            System.out.println(ip.serviceTypeName() + "::" + ip.elementName() + " will be injected with " + result);
        }
        return result;
    }

    static boolean contains(Collection<Qualifier> qualifiers,
                            Class<? extends Annotation> anno) {
        return qualifiers.stream().anyMatch(it -> it.typeName().name().equals(anno.getName()));
    }

}
