# Contributing

Thanks for improving the document format checker.

## Development setup

1. Install Java 17+, Maven 3.9+, Node.js 20+, MySQL 8.0 and Redis 7.
2. Create the database and account using the steps in `README.md`.
3. Run the backend with the `dev` profile and the frontend with `npm run dev`.

## Before opening a pull request

```bash
cd backend
mvn test
mvn package -DskipTests

cd ../frontend
npm ci
npm run build
```

- Keep changes focused and add tests for parser, security, or queue behavior changes.
- Do not commit real `.docx` files, database dumps, `.env` files, API keys, or
  screenshots containing personal documents.
- Update `README.md` when adding configuration keys or API endpoints.
- Use the existing package structure and keep the service/controller/mapper layering.

## Security

Please report vulnerabilities through GitHub's private security advisory flow, not
in a public issue. See `SECURITY.md`.
