package io.helidon.codegen.api.stability;

import io.helidon.common.Api;

public class IncubatingMethod {

    @Api.Incubating
    public static String incubate() {
        return "Hi";
    }
}
