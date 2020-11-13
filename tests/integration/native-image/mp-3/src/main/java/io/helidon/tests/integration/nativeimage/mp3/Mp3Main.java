package io.helidon.tests.integration.nativeimage.mp3;

import io.helidon.microprofile.cdi.Main;

/**
 * Main class.
 * This is present for modularized java to work correctly,
 * as using a main class from a different module resulted
 * in build errors.
 */
public class Mp3Main {
    /**
     * Main method.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        Main.main(args);
    }
}
