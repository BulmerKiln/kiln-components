# kiln-crypto

Envelope encryption for Kiln's secrets: a fresh data key per value, wrapped by a key-encryption
key that never leaves its provider.

## At a glance

| | |
|---|---|
| Kind | Foundation |
| Artifact | `com.bulmerkiln:kiln-crypto` |
| Runs in | Kiln's server |
| Depends on | [`kiln-core`](../kiln-core), Tink, Spring Boot auto-configuration |
| Used by | Nothing yet; `kiln-auth` will seal token-signing keys with it |
| Status | M1, Experimental |

## Purpose

Kiln stores secrets it must keep secret at rest: the private halves of each organization's
signing keys and the tokens it issues. Storing them in the database in the clear would let a
database compromise hand over every organization's signing keys at once. Envelope encryption
stops that: each value is encrypted with its own data key, and the data key is wrapped by a
key-encryption key (KEK) the operator controls, which a KMS-backed provider never lets leave the
KMS. Losing the database leaks ciphertext; the KEK is the operator's to protect.

## Responsibilities

**Does**

- Seal and open values with Tink's envelope encryption: a fresh AES-256-GCM data key per value
- Bind each value to a context as associated data, so a ciphertext can't be moved to another row
- Hold the KEK behind `KeyProvider`, and rewrap values after a rotation
- Provide `FileKeyProvider`, a KEK kept as a Tink JSON keyset in a file, and the auto-configuration
  that wires it in

**Does not**

- Store anything; the caller stores the `SealedValue` it gets back
- Decide which values to seal or what context to use; the caller does
- Rotate the KEK on a schedule; the operator rotates it and values are rewrapped as they're read

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| CRYPTO-1 | The key-encryption key (KEK) must only ever wrap and unwrap data keys, never encrypt a value itself; a provider backed by a KMS must never let the KEK leave it | `CryptoAutoConfigurationTest` |
| CRYPTO-2 | Every sealed value must use a fresh AES-256-GCM data key, wrapped by the KEK, so one value's key never opens another's and the plaintext never appears in the ciphertext | `EnvelopeServiceTest` |
| CRYPTO-3 | Each value must be bound to a context, such as `signing-key:<organization>:<kid>`, as associated data, so a ciphertext moved to another row or opened under another context can't be opened | `EnvelopeServiceTest` |
| CRYPTO-4 | A wrong context, a tampered ciphertext and a KEK that no longer holds the key must all fail the same way: a `CryptoException` that names none of them and carries no key material or plaintext | `EnvelopeServiceTest` (fuzz target) |
| CRYPTO-5 | After a rotation, values sealed under the old KEK must still open, and `rewrap` must move them to the current KEK, leaving a value already under it untouched | `EnvelopeServiceTest` |
| CRYPTO-6 | A file KEK must be a Tink JSON keyset in which every key is AES-256-GCM, at most 64 KiB, and not writable by anyone but its owner; anything else stops the boot, naming the file and the problem but never its contents, and no input may fail with anything but a `CryptoException` | `FileKeyProviderTest` (fuzz target) |
| CRYPTO-7 | With `generate-kek-if-missing` a missing KEK file is created first, readable only by its owner, and a reload keeps the key; without it a missing file stops the boot, so a mistyped path never yields a fresh KEK that can open nothing | `FileKeyProviderTest`, `CryptoAutoConfigurationTest` |
| CRYPTO-8 | The auto-configuration must provide the file KEK by default, an assembly's own `KeyProvider` (such as a KMS) must replace it, and a missing `kiln.crypto.kek-file` must stop the boot | `CryptoAutoConfigurationTest` |

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.crypto.kek-file` | none, required | The Tink JSON keyset holding the key-encryption key |
| `kiln.crypto.generate-kek-if-missing` | `false` | Create `kek-file` with a new key when it doesn't exist, for evaluation installs |

## Usage

```java
SealedValue sealed = envelope.seal(privateKey, "signing-key:acme:k1");
// ... store sealed.kekId() and sealed.ciphertext() ...
byte[] key = envelope.open(sealed, "signing-key:acme:k1");
```

## Making a KEK

Generate the keyset with Tinkey, Tink's CLI, and point `kiln.crypto.kek-file` at it:

```bash
tinkey create-keyset --key-template AES256_GCM --output kek.json
chmod 600 kek.json
```

To rotate, add a new key and make it primary; the old key stays in the keyset so values sealed
under it keep opening until they're rewrapped:

```bash
tinkey add-key --key-template AES256_GCM --keyset kek.json --make-primary
```

## Security

- **Threats**: a database compromise exposing every organization's signing keys and tokens; a
  ciphertext copied to another row or organization being opened there; a KEK file anyone can
  rewrite; a mistyped KEK path silently creating a fresh key that can open nothing
- **Untrusted input**: stored ciphertexts and KEK files, which may be tampered with or corrupted
- **Defaults**: a missing `kiln.crypto.kek-file` stops the boot (CRYPTO-8); a missing file is never
  created unless `generate-kek-if-missing` is on (CRYPTO-7)
- **Protections**: a KEK that only wraps (CRYPTO-1), a fresh data key per value (CRYPTO-2),
  context binding (CRYPTO-3), one failure for every bad open (CRYPTO-4), rewrap after a rotation
  (CRYPTO-5), a file KEK that refuses anything but a safe keyset (CRYPTO-6)

## Testing

- **Contract suite**: none
- **Fuzz targets**: opening arbitrary stored bytes (`EnvelopeServiceTest`), loading arbitrary keyset
  files (`FileKeyProviderTest`)
- **Other**: rotation and rewrap, keyset validation, the auto-configuration

```bash
./gradlew :kiln-crypto:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: AEAD associated data (the context binding); [Tink's envelope encryption](https://github.com/google/tink) (`KmsEnvelopeAead`)
- **Dependencies**: [Google Tink](https://github.com/google/tink), [Tinkey](https://github.com/google/tink) (keyset tooling)
- **Reference projects**: Tink's `KmsEnvelopeAead` and its file keyset format

## Status

Implemented: CRYPTO-1 to CRYPTO-8.

Planned (M1, auth phase): a KMS-backed `KeyProvider`, and the `signing_keys` and `tokens` tables
that seal their private halves and secrets through this module.