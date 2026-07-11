# Bravoscribe Admin Back-office

Angular 22 admin back-office for Bravoscribe — see `SPEC.md` in this folder and the
root `SYSTEM_SPEC.md` for the full specification. Scaffolded with
[Angular CLI](https://github.com/angular/angular-cli) version 22.0.6.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

This project uses [Jest](https://jestjs.io/) + [Angular Testing Library](https://testing-library.com/docs/angular-testing-library/intro/)
per `SPEC.md`, not the Angular CLI's default test runner. Run:

```bash
npm test
```

## Local backend

By default `src/environments/environment.ts` points at `http://localhost:8080` — the
nginx gateway from `infra/docker-compose.yml`. Start the backend stack per the root
`README.md` before running `ng serve`.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
