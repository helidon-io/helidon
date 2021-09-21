OIDC
----

New Open ID Connect features in Helidon.

# OIDC Logout
The capability to logout requires two pieces of information:
1. The token (JWT) that we usually have in a cookie or in a header
2. The ID token, that we get when obtaining JWT using the code flow 

As we need both, we need to store both of these tokens in a cookie (or get them from a header).
This also requires encrypting these tokens, as the ID token is not public information.

To achieve this, we need

1. either configuration of encryption as part of OIDC configuration,
 or use `Security` instance registered in global context (and named encryption/decryption configured).
2. support for encrypted JWT and capability to encrypt existing JWT ourselves

