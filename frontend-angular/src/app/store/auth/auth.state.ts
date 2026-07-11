import { AdminRole, SessionStatus } from '../../core/models/auth.model';

export interface AuthState {
  // 'checking' — app just booted, silent refresh in flight, guards must wait
  // 'anonymous' — no valid session
  // 'authenticated' — accessToken held in memory, role decoded from it
  status: SessionStatus;
  accessToken: string | null;
  userId: string | null;
  role: AdminRole | null;
  loginPending: boolean;
  loginError: string | null;
}

export const initialAuthState: AuthState = {
  status: 'checking',
  accessToken: null,
  userId: null,
  role: null,
  loginPending: false,
  loginError: null,
};

export const AUTH_FEATURE_KEY = 'auth';
