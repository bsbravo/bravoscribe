import { AccessTokenClaims } from '../models/auth.model';

/**
 * Decodes the payload of a JWT without verifying its signature.
 * Verification is the server's job (RS256, public key) — this is purely so the
 * UI can react to the claims (role, expiry) without a network round-trip.
 * Never trust this decoding for an authorization decision that matters;
 * every admin endpoint enforces `hasAuthority('ADMIN')` server-side regardless.
 */
export function decodeAccessToken(token: string): AccessTokenClaims | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) {
      return null;
    }
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const json = decodeURIComponent(
      atob(padded)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    const claims = JSON.parse(json) as AccessTokenClaims;
    if (!claims.sub || !claims.role) {
      return null;
    }
    return claims;
  } catch {
    return null;
  }
}

export function isExpired(claims: AccessTokenClaims): boolean {
  return claims.exp * 1000 <= Date.now();
}
