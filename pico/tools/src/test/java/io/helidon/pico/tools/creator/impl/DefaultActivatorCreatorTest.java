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

package io.helidon.pico.tools.creator.impl;

import java.util.Collections;

import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.testsubjects.hello.HelloImpl;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.ActivatorCreator;
import io.helidon.pico.tools.creator.ActivatorCreatorResponse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link io.helidon.pico.tools.creator.impl.DefaultActivatorCreator}.
 */
public class DefaultActivatorCreatorTest extends AbstractBaseCreator {

    private final ActivatorCreator activatorCreator = loadAndCreate(ActivatorCreator.class);

    /**
     * Sanity testing.
     */
    @Test
    public void sanity() {
        assertNotNull(activatorCreator);
        assertEquals(DefaultActivatorCreator.class, activatorCreator.getClass());
    }

    /**
     * Most of the testing will need to occur downstream from this module.
     */
    @Test
    public void codegenHelloActivator() {
        DefaultActivatorCreator activatorCreator = (DefaultActivatorCreator) this.activatorCreator;
        DefaultActivatorCreatorRequest req = DefaultActivatorCreatorRequest.builder()
                .serviceTypeNames(Collections.singletonList(DefaultTypeName.create(HelloImpl.class)))
                .codeGenPaths(DefaultGeneralCodeGenPaths.builder().build())
                .build();
        assertNotNull(req.getConfigOptions());
        assertTrue(req.isFailOnError());
        assertNull(req.getCodeGenRequest());

        ToolsException te = assertThrows(ToolsException.class, () -> activatorCreator.createModuleActivators(req));
        assertEquals("an annotation processor env is required", te.getMessage());

        DefaultActivatorCreatorRequest req2 = (DefaultActivatorCreatorRequest) DefaultActivatorCreatorRequest.builder()
                .serviceTypeNames(Collections.singletonList(DefaultTypeName.create(HelloImpl.class)))
                .codeGenPaths(DefaultGeneralCodeGenPaths.builder().build())
                .failOnError(Boolean.FALSE)
                .build();
        ActivatorCreatorResponse res = activatorCreator.createModuleActivators(req2);
        assertNotNull(res);
        assertFalse(res.isSuccess());
        assertEquals("an annotation processor env is required", res.getError().getMessage());
    }

}
