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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class CdiWithInjected {
    private final TestCdiBean cdiBean;
    private final TestBothBean bothBean;
    private final TestMicronautBean micronautBean;

    @Inject
    public CdiWithInjected(TestCdiBean cdiBean, TestBothBean bothBean, TestMicronautBean micronautBean) {
        this.cdiBean = cdiBean;
        this.bothBean = bothBean;
        this.micronautBean = micronautBean;
    }

    TestCdiBean cdiBean() {
        return cdiBean;
    }

    TestBothBean bothBean() {
        return bothBean;
    }

    TestMicronautBean micronautBean() {
        return micronautBean;
    }
}
