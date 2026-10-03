// Everything goes through the API gateway: the browser never talks to individual services.
export const API_BASE = window.__API_BASE__ || 'http://localhost:8080';
