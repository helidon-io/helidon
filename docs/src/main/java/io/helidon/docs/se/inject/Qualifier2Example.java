package io.helidon.docs.se.inject;

import io.helidon.service.registry.Service;

class Qualifier2Example {

    // tag::snippet_1[]
    /**
     * Custom Helidon Inject qualifier.
     */
    @Service.Qualifier
    public @interface HexCode {
        String value();
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    interface Color {
        String name();
    }

    @HexCode("0000FF")
    @Service.Singleton
    static class BlueColor implements Color {

        @Override
        public String name() {
            return "blue";
        }
    }

    @HexCode("008000")
    @Service.Singleton
    static class GreenColor implements Color {

        @Override
        public String name() {
            return "green";
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @Service.Singleton
    record BlueCircle(@HexCode("0000FF") Color color) {
    }

    @Service.Singleton
    record GreenCircle(@HexCode("008000") Color color) {
    }
    // end::snippet_3[]


}
