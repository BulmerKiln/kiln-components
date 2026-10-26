# Recordings

Real output from `ghcr.io/gitleaks/gitleaks:v8.30.1`, captured on 2026-10-26, on a `config.env`
holding a fake AWS key, and on a clean one:

```
gitleaks dir <dir> --report-format json --report-path - --no-banner [--redact]
```

`unredacted` was recorded without `--redact`, with the key replaced by `%SECRET%`, so the
contract can prove the adapter never copies a secret even if redaction were lost. Exit code 1
with leaks, 0 without.
