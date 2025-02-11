/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import io.helidon.common.types.Annotation;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

final class ServicesFactoryTypes {
    private ServicesFactoryTypes() {
    }

    @Target({ElementType.TYPE, ElementType.PARAMETER})
    @Retention(RetentionPolicy.CLASS)
    @Service.Qualifier
    @interface FirstQualifier {
        Qualifier QUALIFIER = Qualifier.create(Annotation.create(FirstQualifier.class));
    }

    @Target({ElementType.TYPE, ElementType.PARAMETER})
    @Retention(RetentionPolicy.CLASS)
    @Service.Qualifier
    @interface SecondQualifier {
        Qualifier QUALIFIER = Qualifier.create(Annotation.create(SecondQualifier.class));
    }

    interface QualifiedContract {
        String description();
    }

    @Service.Singleton
    static class ContractFactory implements Service.ServicesFactory<QualifiedContract> {

        @Override
        public List<Service.QualifiedInstance<QualifiedContract>> services() {
            return List.of(
                    Service.QualifiedInstance.create(new QualifiedImpl("first"), FirstQualifier.QUALIFIER),
                    Service.QualifiedInstance.create(new QualifiedImpl("second"), SecondQualifier.QUALIFIER),
                    Service.QualifiedInstance.create(new QualifiedImpl("both"),
                                                     FirstQualifier.QUALIFIER,
                                                     SecondQualifier.QUALIFIER)
            );
        }
    }

    @Service.Singleton
    static class QualifiedReceiver {
        private final QualifiedContract first;
        private final QualifiedContract second;
        private final QualifiedContract third;

        @Service.Inject
        QualifiedReceiver(@FirstQualifier QualifiedContract first,
                          @SecondQualifier QualifiedContract second,
                          @FirstQualifier @SecondQualifier QualifiedContract third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }

        QualifiedContract first() {
            return first;
        }

        QualifiedContract second() {
            return second;
        }

        QualifiedContract third() {
            return third;
        }
    }

    static class QualifiedImpl implements QualifiedContract {
        private final String name;

        QualifiedImpl(String name) {
            this.name = name;
        }

        @Override
        public String description() {
            return name;
        }
    }
}
