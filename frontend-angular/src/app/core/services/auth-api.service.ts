import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AccessTokenResponse } from '../models/auth.model';
import { API_BASE_URL } from '../tokens/api-base-url.token';

/**
 * Talks to services/user-service auth endpoints only (POST /login, /refresh, DELETE /logout).
 * The refresh token itself is an httpOnly cookie set by the server — this service never
 * sees or stores it. `withCredentials: true` is required so the browser attaches/receives
 * that cookie across requests.
 */
@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  login(email: string, password: string): Observable<AccessTokenResponse> {
    return this.http.post<AccessTokenResponse>(
      `${this.baseUrl}/api/users/login`,
      { email, password },
      { withCredentials: true },
    );
  }

  /** Attempts to mint a new access token from the httpOnly refreshToken cookie, if any. */
  refresh(): Observable<AccessTokenResponse> {
    return this.http.post<AccessTokenResponse>(
      `${this.baseUrl}/api/users/refresh`,
      {},
      { withCredentials: true },
    );
  }

  logout(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/users/logout`, { withCredentials: true });
  }
}
