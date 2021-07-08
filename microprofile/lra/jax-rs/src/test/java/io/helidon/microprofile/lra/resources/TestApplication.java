/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.helidon.microprofile.lra.resources;

import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.core.Application;


@ApplicationScoped
public class TestApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(
                CdiCompleteOrCompensate.class,
                StartAndAfter.class,
                JaxRsCompleteOrCompensate.class,
                DontEnd.class,
                Timeout.class,
                Recovery.class,
                RecoveryStatus.class,
                CdiNestedCompleteOrCompensate.class,
                JaxRsNestedCompleteOrCompensate.class
        );
    }

}
