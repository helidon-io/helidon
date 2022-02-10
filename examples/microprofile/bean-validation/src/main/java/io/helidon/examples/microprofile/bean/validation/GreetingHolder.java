package io.helidon.examples.microprofile.bean.validation;


import jakarta.validation.constraints.NotNull;

/**
 * Holder for a greeting, ensuring it is not null.
 */
public class GreetingHolder {

    /**
     * Explicitly declare as not eligible for null.
     */
    @NotNull
    private String greeting;

    GreetingHolder(String greeting) {
        this.greeting = greeting;
    }

    String getGreeting() {
        return greeting;
    }

}

