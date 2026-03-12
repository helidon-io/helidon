package io.helidon.json.smile;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint
interface SmileConfigBlueprint {

    @Option.DefaultBoolean(true)
    boolean sharedKeyStrings();

    boolean sharedValueStrings();

    boolean rawBinaryEnabled();

    boolean emitEndMark();

}
