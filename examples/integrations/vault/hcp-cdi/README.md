HCP Vault Integration with Reactive APIs
---

This example expects an empty Vault. It uses the token to create all required resources.

To run this example:

1. Run a docker image with a known root token

```shell
docker run --cap-add=IPC_LOCK -e VAULT_DEV_ROOT_TOKEN_ID=myroot -d --name=vault -p8200:8200 vault
```

2. Build this application

```shell
mvn clean package
```

3. Start this application

```shell
java -jar ./target/
```

4. Exercise the endpoints