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
    private final CdiBean cdiBean;
    private final BothBean bothBean;
    private final MicronautBean micronautBean;

    @Inject
    public CdiWithInjected(CdiBean cdiBean, BothBean bothBean, MicronautBean micronautBean) {
        this.cdiBean = cdiBean;
        this.bothBean = bothBean;
        this.micronautBean = micronautBean;
    }

    CdiBean cdiBean() {
        return cdiBean;
    }

    BothBean bothBean() {
        return bothBean;
    }

    MicronautBean micronautBean() {
        return micronautBean;
    }
}
