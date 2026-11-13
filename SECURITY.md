# Security Policy

## Supported versions

Only the current `main` branch is supported with security updates.

## Reporting a vulnerability

Please use GitHub's private **Security → Report a vulnerability** flow for this repository.

Do not open a public issue for an unpatched vulnerability, and do not include real
credentials, personal documents, or production database dumps in reports.

## Deployment notes

- The default `admin / admin123` account is for local demonstration only. Change
  `DEMO_PASSWORD` before exposing the service to any network.
- `doccheck123` and `root123456` in the example configuration are local development
  defaults. Use strong, environment-specific values in production.
- Keep `AUTH_TRUST_PROXY=false` unless a trusted reverse proxy overwrites
  `X-Forwarded-For` / `X-Real-IP`.
- Set `DSH_API_KEY` through an environment variable or secret manager. Never commit
  a real model API key.
- Uploaded DOCX files are untrusted input. Keep the built-in ZIP-bomb checks enabled
  and enforce the 50 MB multipart limit at the reverse proxy as well.
- Redis tokens and queue state should be protected with a password and network ACL
  in any shared environment.
