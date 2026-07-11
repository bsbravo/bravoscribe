// Production environment — Azure Static Web Apps build.
// API_BASE_URL per frontend-angular/SPEC.md — Azure API Management (APIM) endpoint.
// Replace with the real APIM URL for the target environment at build time.
export const environment = {
  production: true,
  apiBaseUrl: 'https://REPLACE-WITH-APIM-NAME.azure-api.net',
};
