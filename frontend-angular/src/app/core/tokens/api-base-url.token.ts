import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

/** Base URL of the API gateway (nginx locally, Azure APIM in cloud). */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => environment.apiBaseUrl,
});
