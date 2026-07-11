import { AuthActions } from './auth.actions';
import { authReducer } from './auth.reducer';
import { initialAuthState } from './auth.state';

// Header.payload.signature — payload is {"sub":"11111111-1111-1111-1111-111111111111","role":"ADMIN","iat":0,"exp":9999999999}
const ADMIN_TOKEN =
  'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJpYXQiOjAsImV4cCI6OTk5OTk5OTk5OX0.sig';
// payload role USER, same sub
const USER_TOKEN =
  'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiVVNFUiIsImlhdCI6MCwiZXhwIjo5OTk5OTk5OTk5fQ.sig';

describe('authReducer', () => {
  it('returns the initial state for an unknown action', () => {
    const state = authReducer(undefined, { type: '@@INIT' } as never);
    expect(state).toEqual(initialAuthState);
  });

  it('sessionRestored decodes role and userId from the access token', () => {
    const state = authReducer(initialAuthState, AuthActions.sessionRestored({ accessToken: ADMIN_TOKEN }));
    expect(state.status).toBe('authenticated');
    expect(state.role).toBe('ADMIN');
    expect(state.userId).toBe('11111111-1111-1111-1111-111111111111');
  });

  it('sessionAbsent resets to anonymous', () => {
    const authenticated = authReducer(
      initialAuthState,
      AuthActions.sessionRestored({ accessToken: ADMIN_TOKEN }),
    );
    const state = authReducer(authenticated, AuthActions.sessionAbsent());
    expect(state.status).toBe('anonymous');
    expect(state.accessToken).toBeNull();
  });

  it('loginSuccess with a USER-role token is still decoded as USER (guard rejects it, not the reducer)', () => {
    const state = authReducer(initialAuthState, AuthActions.loginSuccess({ accessToken: USER_TOKEN }));
    expect(state.role).toBe('USER');
  });

  it('loginForbidden clears any token and sets an explanatory error', () => {
    const state = authReducer(initialAuthState, AuthActions.loginForbidden());
    expect(state.status).toBe('anonymous');
    expect(state.accessToken).toBeNull();
    expect(state.loginError).toContain('admin access');
  });

  it('sessionEnded always resets to a clean anonymous state', () => {
    const authenticated = authReducer(
      initialAuthState,
      AuthActions.sessionRestored({ accessToken: ADMIN_TOKEN }),
    );
    const state = authReducer(authenticated, AuthActions.sessionEnded({ reason: 'expired' }));
    expect(state).toEqual({ ...initialAuthState, status: 'anonymous' });
  });
});
