/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.spi.impl;

import java.util.Set;

import io.helidon.pico.example.Hello;
import io.helidon.pico.example.HelloImpl;
import io.helidon.pico.example.HelloImpl$$picodiActivator;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceInfoBasics;

import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.DefaultQualifierAndValue.create;
import static io.helidon.pico.DefaultServiceInfo.builder;
import static io.helidon.pico.DefaultServiceInfo.matchesQualifiers;
import static io.helidon.pico.DefaultServiceInfo.toServiceInfoFromClass;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link io.helidon.pico.DefaultServiceInfo}.
 */
public class DefaultServiceInfoTest {

    @Test
    public void testSimpleBuilder() {
        DefaultServiceInfo serviceInfo1 = builder()
                .serviceTypeName(HelloImpl.class.getName())
                .activatorTypeName(HelloImpl$$picodiActivator.class.getName())
                .contractImplemented(Hello.class.getName())
                .build();
        DefaultServiceInfo serviceInfo2 = builder()
                .serviceTypeName(HelloImpl.class.getName())
                .activatorTypeName(HelloImpl$$picodiActivator.class.getName())
                .contractImplemented(Hello.class.getName())
                .build();
        assertEquals(serviceInfo1, serviceInfo2);

        serviceInfo2 = serviceInfo2.toBuilder().build();
        assertEquals(serviceInfo1, serviceInfo2);

        serviceInfo2 = serviceInfo2.toBuilder().build();
        assertEquals(serviceInfo1, serviceInfo2);

        serviceInfo2 = serviceInfo2.toBuilder().runLevel(1).build();
        assertNotEquals(serviceInfo1, serviceInfo2);
    }

    @Test
    public void toServiceInfo() {
        DefaultServiceInfo serviceInfo1 = builder()
                .scopeTypeName(Singleton.class.getName())
                .serviceTypeName(HelloImpl.class.getName())
                .contractImplemented(Hello.class.getName())
                .qualifier(DefaultQualifierAndValue.createNamed("name"))
                .weight(1D)
                .runLevel(2)
                .build();
        assertSame(serviceInfo1, toServiceInfoFromClass(HelloImpl.class, serviceInfo1));

        ServiceInfoBasics serviceInfoBasics = new ServiceInfoBasics() {
            @Override
            public double weight() {
                return 1;
            }

            @Override
            public String serviceTypeName() {
                return HelloImpl.class.getName();
            }

            @Override
            public Set<QualifierAndValue> qualifiers() {
                return singleton(DefaultQualifierAndValue.createNamed("name"));
            }

            @Override
            public Set<String> contractsImplemented() {
                return singleton(Hello.class.getName());
            }

            @Override
            public Integer runLevel() {
                return 2;
            }
        };

        DefaultServiceInfo serviceInfo = toServiceInfoFromClass(HelloImpl.class, serviceInfoBasics);
        assertNotSame(serviceInfo1, serviceInfo);
        assertEquals(serviceInfo1, serviceInfo);
    }

    @Test
    public void matches() {
        DefaultServiceInfo src = DefaultServiceInfo.builder()
                .contractImplemented("Hammer")
                .serviceTypeName("BigHammer")
                .qualifiers(Set.of(create(jakarta.inject.Named.class, "big"), create(Preferred.class)))
                .build();
        ServiceInfo criteria = DefaultServiceInfo.builder()
                .contractImplemented("Hammer")
                .qualifiers(singleton(create(Preferred.class)))
                .build();
        assertTrue(src.matches(criteria));
    }

    @Test
    public void testMatchesQualifiersWithWildCards() {
        Set<QualifierAndValue> src = singleton(DefaultQualifierAndValue.WILDCARD_NAMED);
        Set<QualifierAndValue> criteria = singleton(create(Preferred.class));
        assertTrue(matchesQualifiers(src, criteria));
        assertFalse(matchesQualifiers(criteria, src));

        criteria = singleton(create(jakarta.inject.Named.class, "whatever-value"));
        assertTrue(matchesQualifiers(src, criteria));
        assertTrue(matchesQualifiers(criteria, src));

        criteria = singleton(create(jakarta.inject.Named.class, (String) null));
        assertTrue(matchesQualifiers(src, criteria));
        assertTrue(matchesQualifiers(criteria, src));
    }

    @Test
    public void testMatchesQualifiers() {
        Set<QualifierAndValue> src = singleton(create(Preferred.class, "whatever-value"));
        Set<QualifierAndValue> criteria = singleton(create(Preferred.class));
        assertTrue(matchesQualifiers(src, criteria));
        assertFalse(matchesQualifiers(criteria, src));
    }


    @Qualifier
    public @interface Preferred {
        String value() default "";
    }

}
