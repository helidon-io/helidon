# Helidon Config Changes Example

This example shows how an application can deal with changes to 
configuration.

## Change notification

[`OnChangeExample.java`](./src/main/java/io/helidon/config/examples/changes/OnChangeExample.java):
uses `Config.onChange`, passing either a method reference (a lambda expression
would also work) which the config system invokes when the config source changes
)

## Latest-value supplier

Recall that once your application obtains a `Config` instance, its config values 
do not change. The 
[`AsSupplierExample.java`](./src/main/java/io/helidon/config/examples/changes/AsSupplierExample.java)
example shows how your application can get a config _supplier_ that always reports 
the latest config value for a key, including any changes made after your
application obtained the `Config` object. Although this approach does not notify
your application _when_ changes occur, it _does_ permit your code to always use 
the most up-to-date value. Sometimes that is all you need.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-config-changes.jar
```
