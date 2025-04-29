/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.features;

import io.helidon.common.features.api.HelidonFlavor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/*
 * Make sure that features do not throw an exception when no features registered
 * (used to throw a NPE, this test is here to prevent that).
 */
class HelidonFeaturesTest {

    @BeforeEach
    void reset() {
        HelidonFeatures.PRINTED.set(false);
        HelidonFeatures.CURRENT_FLAVOR.set(null);
    }
    @Test
    void testNoFeaturesSe() {
        HelidonFeatures.flavor(HelidonFlavor.SE);
        HelidonFeatures.features(HelidonFlavor.SE, "VERSION", false);
    }

    @Test
    void testNoFeaturesDetailsSe() {
        HelidonFeatures.flavor(HelidonFlavor.SE);
        HelidonFeatures.features(HelidonFlavor.SE, "VERSION", true);
    }

    @Test
    void testNoFeaturesMp() {
        HelidonFeatures.flavor(HelidonFlavor.MP);
        HelidonFeatures.features(HelidonFlavor.MP, "VERSION", false);
    }

    @Test
    void testNoFeaturesDetailsMp() {
        HelidonFeatures.flavor(HelidonFlavor.MP);
        HelidonFeatures.features(HelidonFlavor.MP, "VERSION", true);
    }
}
