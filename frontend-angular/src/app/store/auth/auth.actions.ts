import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { SessionEndReason } from '../../core/models/auth.model';

export const AuthActions = createActionGroup({
  source: 'Auth',
  events: {
    // Dispatched once by the root component on app bootstrap.
    'App Initialized': emptyProps(),
    'Session Restored': props<{ accessToken: string }>(),
    'Session Absent': emptyProps(),

    'Login Submitted': props<{ email: string; password: string }>(),
    'Login Success': props<{ accessToken: string }>(),
    'Login Failure': props<{ message: string }>(),
    // Login succeeded against the User Service, but the account is not an admin.
    'Login Forbidden': emptyProps(),

    'Logout Requested': emptyProps(),
    // Terminal action for every way a session can end (logout, 401, 403).
    'Session Ended': props<{ reason: SessionEndReason }>(),
  },
});
