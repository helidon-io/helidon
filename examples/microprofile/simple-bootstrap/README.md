# Helidon MP Simple Bootstrap Example

This examples shows a simple application written using Helidon MP to illustrate the usage of [Bootstrap CSS](https://getbootstrap.com/).

```bash
mvn package
java -jar target/helidon-examples-microprofile-simple-bootstrap.jar
```

Then try the url which will show up the default page.

```
curl -X GET http://localhost:7001/
```

By default the server will use a dynamic port, see the messages displayed when the application starts.

### Changes to config.properties

Below lines are added to `microprofile-config.properties` for the application to load the `index.html` as default.
```
server.static.classpath.location=/webapp
server.static.classpath.welcome=index.html
```

## Bootstrap CSS

The binary of minifgied Bootstrap CSS is included at `/src/main/resources/webapp/css`

The css is referenced in `index.html` as below
```
<link href="css/bootstrap-4.5.2.min.css" rel="stylesheet">
```

Alternatively Bootstrap CSS can also be included using a CDN url instead of the binary file.
- Remove the file `examples/microprofile/simple-bootstrap/src/main/resources/webapp/css/bootstrap-4.5.2.min.css`
- Change code in `index.html` as below:
```
<link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
```

## Resources
- The html and css sources used in this example are referred from https://getbootstrap.com/docs/4.5/examples/jumbotron/
