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

package io.helidon.microprofile.cdi.lang.model.tck;

import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import org.jboss.cdi.lang.model.tck.LangModelVerifier;

public class LangModelExtension implements BuildCompatibleExtension {

    public static int ENHANCEMENT_INVOKED = 0;

    public LangModelExtension(){}

    @Enhancement(types = LangModelVerifier.class)
    public void run(ClassInfo clazz) {
        ENHANCEMENT_INVOKED++;
        LangModelVerifier.verify(clazz);
    }

    @Discovery
    public void addVerifier(ScannedClasses sc) {
        sc.add(LangModelVerifier.class.getName());
    }
}

