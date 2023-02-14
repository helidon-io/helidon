package io.helidon.nima.http.processor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class Templates {
    private Templates() {
    }

    static String loadTemplate(String templateProfile, String name) {
        String path = "templates/pico/" + templateProfile + "/" + name;
        try {
            InputStream in = Templates.class.getClassLoader().getResourceAsStream(path);
            if (in == null) {
                throw new RuntimeException("Could not find template " + path + " on classpath.");
            }
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
