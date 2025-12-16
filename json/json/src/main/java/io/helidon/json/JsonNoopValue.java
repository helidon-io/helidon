package io.helidon.json;

/**
 * This object is never returned anywhere and is used as a placeholder.
 */
final class JsonNoopValue extends JsonValue {

    static final JsonNoopValue INSTANCE = new JsonNoopValue();

    private JsonNoopValue() {
    }

    @Override
    public JsonValueType type() {
        //Intentional to avoid adding unwanted values to the enum
        return null;
    }

    @Override
    public void toJson(Generator generator) {
        throw new UnsupportedOperationException("This is noop placeholder value. Serialization is not supported");
    }

    @Override
    byte jsonStartChar() {
        throw new JsonException("No json values are remaining");
    }
}
