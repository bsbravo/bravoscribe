export type AdminRole = 'ADMIN' | 'USER';

// Mirrors com.bravoscribe.userservice.dto.AccessTokenResponse.
export interface AccessTokenResponse {
  accessToken: string;
}

// Decoded claims from the RS256 access token (services/user-service JwtService).
// The token is never verified client-side — the server is the source of truth for
// authorization. Decoding here is for UX only (showing the right nav, guarding routes
// before an API round-trip). A USER-role token that reaches an admin endpoint is still
// rejected server-side with 403 regardless of what the client believes.
export interface AccessTokenClaims {
  sub: string; // userId
  role: AdminRole;
  iat: number;
  exp: number;
}

export type SessionStatus = 'checking' | 'anonymous' | 'authenticated';

export type SessionEndReason = 'logout' | 'expired' | 'forbidden';
