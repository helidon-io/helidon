package io.helidon.builder.tests.factory;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint
interface UsingBlueprint {
    @Option.Singular
    List<D> ds();
}
