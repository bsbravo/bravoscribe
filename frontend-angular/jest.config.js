// SPEC.md mandates Jest + Angular Testing Library as the test stack.
module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  // e2e/ holds Playwright specs (a separate test runner) — Jest's default
  // testMatch picks up *.spec.ts regardless of directory, so it'd otherwise
  // try to execute them and fail on Playwright's `test`/`expect` imports.
  // `roots` (a directory allowlist) rather than testPathIgnorePatterns (a
  // regex denylist) — the latter breaks on Windows because <rootDir>
  // resolves with backslashes, which regex then treats as escape sequences.
  roots: ['<rootDir>/src'],
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/dist/'],
  transformIgnorePatterns: ['node_modules/(?!.*\\.mjs$)'],
};
