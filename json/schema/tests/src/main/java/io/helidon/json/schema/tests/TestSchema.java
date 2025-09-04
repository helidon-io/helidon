package io.helidon.json.schema.tests;

import io.helidon.json.schema.JsonSchema;

@JsonSchema.Schema
public class TestSchema {

    @JsonSchema.Title("Test integer")
    @JsonSchema.Description("This integer is intended for a test")
    @JsonSchema.Integer.Minimum(0)
    @JsonSchema.Integer.Maximum(4)
    @JsonSchema.Integer.MultipleOf(2)
    private int testInt;

    @JsonSchema.Title("Test car override")
    @JsonSchema.Description("Car description override")
    @JsonSchema.Required
    @JsonSchema.Ignore
    SchemaCar car;

    public void setTestInt(int testInt) {
        this.testInt = testInt;
    }

    //    public void setCar(SchemaCar car) {
    //        this.car = car;
    //    }
}
