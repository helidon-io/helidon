### Generating the certificates: ###

Enter the directory where this `.md` file is located:

1. Execute: `openssl req -newkey rsa:2048 -nodes -keyout key.pem -x509 -days 99999 -out certificate.pem`

    Provide: `US`, `California`, `Santa Clara`, `Oracle`, `Helidon`, `helidon-webserver-netty-test`, `info@helidon.io`
    Note that shorter RSA certificate may not work due to various restrictions on both client and server side

2. Convert the key from the traditional format to pkcs8: `openssl pkcs8 -topk8 -inform PEM -outform PEM -in key.pem -out key.pkcs8.pem -nocrypt`
3. Execute: `openssl pkcs12 -inkey key.pem -in certificate.pem -export -out certificate.p12`

    Provide password: `helidon`
