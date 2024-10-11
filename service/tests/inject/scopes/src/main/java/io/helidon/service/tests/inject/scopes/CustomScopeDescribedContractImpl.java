package io.helidon.service.tests.inject.scopes;

class CustomScopeDescribedContractImpl implements CustomScopeDescribedContract {
    @Override
    public String message() {
        return "It works!";
    }
}
