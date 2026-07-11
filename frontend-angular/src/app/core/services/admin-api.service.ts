import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { UserPage } from '../models/user.model';
import { API_BASE_URL } from '../tokens/api-base-url.token';

/**
 * Talks to services/user-service AdminController endpoints
 * (GET /api/users, PUT /api/users/{id}/deactivate) — both require ADMIN authority,
 * enforced server-side via @PreAuthorize("hasAuthority('ADMIN')").
 *
 * Note: the User Service (Phase 2) does not expose a "get user by id" or
 * "search users" endpoint, nor a dedicated stats/summary endpoint. The list endpoint
 * only supports page/size. The dashboard and the user detail screen work within this
 * constraint — see users.effects.ts and dashboard.component.ts for how.
 */
@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listUsers(page: number, size: number): Observable<UserPage> {
    return this.http.get<UserPage>(`${this.baseUrl}/api/users`, {
      params: { page, size },
    });
  }

  deactivateUser(id: string): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/api/users/${id}/deactivate`, {});
  }
}
