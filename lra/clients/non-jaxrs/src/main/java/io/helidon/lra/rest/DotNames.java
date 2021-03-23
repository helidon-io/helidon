/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.lra.rest;

import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.jboss.jandex.DotName;

public class DotNames {

    public static final DotName LRA = DotName.createSimple(org.eclipse.microprofile.lra.annotation.ws.rs.LRA.class.getName());
    public static final DotName COMPENSATE = DotName.createSimple(Compensate.class.getName());
    public static final DotName AFTER_LRA = DotName.createSimple(AfterLRA.class.getName());
    public static final DotName OBJECT = DotName.createSimple(Object.class.getName());
}
