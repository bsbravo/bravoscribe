import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideStore, Store } from '@ngrx/store';
import { firstValueFrom, isObservable } from 'rxjs';
import { AuthActions } from '../../store/auth/auth.actions';
import { authReducer } from '../../store/auth/auth.reducer';
import { AUTH_FEATURE_KEY } from '../../store/auth/auth.state';
import { adminGuard } from './admin.guard';

const ADMIN_TOKEN =
  'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJpYXQiOjAsImV4cCI6OTk5OTk5OTk5OX0.sig';
const USER_TOKEN =
  'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiVVNFUiIsImlhdCI6MCwiZXhwIjo5OTk5OTk5OTk5fQ.sig';

async function resolveGuard() {
  const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
  return isObservable(result) ? firstValueFrom(result) : result;
}

describe('adminGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideStore({ [AUTH_FEATURE_KEY]: authReducer })],
    });
  });

  it('blocks navigation and redirects to /login when there is no session', async () => {
    const store = TestBed.inject(Store);
    // Simulate the app-boot session check having resolved to "no session" —
    // the guard is meant to wait out the initial 'checking' status, not race it.
    store.dispatch(AuthActions.sessionAbsent());

    const result = await resolveGuard();
    expect(result).not.toBe(true);
  });

  it('allows navigation for an authenticated ADMIN', async () => {
    const store = TestBed.inject(Store);
    store.dispatch(AuthActions.sessionRestored({ accessToken: ADMIN_TOKEN }));

    const result = await resolveGuard();
    expect(result).toBe(true);
  });

  it('rejects a USER-role token with a redirect (not `true`)', async () => {
    const store = TestBed.inject(Store);
    store.dispatch(AuthActions.sessionRestored({ accessToken: USER_TOKEN }));

    const result = await resolveGuard();
    expect(result).not.toBe(true);
  });
});
