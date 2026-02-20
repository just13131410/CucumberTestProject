# Vault Policy: cucumber-test
# Grants read access to the cucumber-test secrets
#
# Apply with:
#   vault policy write cucumber-test /path/to/vault-policy.hcl

path "secret/data/cucumber-test" {
  capabilities = ["read"]
}
