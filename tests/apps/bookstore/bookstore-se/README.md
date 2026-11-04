# BookApplication

1. Run `mvn clean install` to build your application
2. Start application with `java -Dbookstore.size=10 -jar target/bookstore-se.jar`.
3. To check that your application is running enter url `http://localhost:8080/books`

# JPMS

You can run using modules like this:

`java --module-path target/bookstore-se.jar:target/libs -m io.helidon.tests.apps.bookstore.se/io.helidon.tests.apps.bookstore.se.Main`

## JSON media support

The `app.json-library` setting selects `json` (the default, using Helidon JSON
trees) or `json_binding` (using generated native JSON converters for books and
authors). Media support is registered explicitly and discovery is disabled.
