# Helidon Docs

This project hosts the Helidon documentation and builds the aggregated javadocs.

## Build

Build the docs and javadocs:
```bash
mvn package -Pjavadoc
```

Build just the docs:
```bash
mvn package
```

Build just the javadocs:
```bash
mvn package -Pjavadoc -Dhelidon.sitegen.skip=true
```

## Serve

```bash
mvn sitegen:serve
```

Open http://localhost:8080 in a browser.
