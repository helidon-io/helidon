# Helidon ATP Reactive Examples

This example demonstrates how user can easily retrieve wallet from their ATP instance running in OCI and use information from that wallet to setup DataSource to do Database operations.

It requires a running OCI ATP instance.

Before running the test, make sure to update required properties in `application.yaml`

- oci.atp.ocid: This is OCID of your running ATP instance.
- oci.atp.walletPassword: password to encrypt the keys inside the wallet. The password must be at least 8 characters long and must include at least 1 letter and either 1 numeric character or 1 special character.
- db.tnsNetServiceName: netServiceName of your database running inside OCI ATP as can be found in `tnsnames.ora` file.
- db.userName: User to access your database running inside OCI ATP.
- db.password: Password of user to access your database running inside OCI ATP.

Once you have updated required properties, you can run the example:

```shell script
mvn clean install
java -jar ./target/helidon-examples-integrations-oci-atp-reactive.jar
```  

To verify that, you can retrieve wallet and do database operation:

```text
http://localhost:8080/atp/wallet
```

You should see `Hello world!!`
