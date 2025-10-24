package my.pkg;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class BookTypeAdapterFactory implements TypeAdapterFactory {

    private static final TypeAdapter<Book> instance = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter writer, Book book) throws IOException {
            writer.beginObject();
            writer.name("title");
            writer.value(book.title());
            writer.name("pages");
            writer.value(book.pages());
            writer.endObject();
        }

        @Override
        public Book read(JsonReader reader) throws IOException {
            reader.beginObject();
            String title = null;
            int pages = 0;
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "title" -> title = reader.nextString();
                    case "pages" -> pages = reader.nextInt();
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
            return new Book(title, pages);
        }
    };

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        if (typeToken.getRawType().isAssignableFrom(Book.class)) {
            return (TypeAdapter<T>) instance;
        }
        return null;
    }
}