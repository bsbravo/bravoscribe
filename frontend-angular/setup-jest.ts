// This app is zoneless (no zone.js polyfill — see src/app/app.config.ts),
// so tests use jest-preset-angular's zoneless test environment setup.
import { setupZonelessTestEnv } from 'jest-preset-angular/setup-env/zoneless';

setupZonelessTestEnv();
