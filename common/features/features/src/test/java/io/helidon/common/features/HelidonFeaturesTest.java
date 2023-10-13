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
