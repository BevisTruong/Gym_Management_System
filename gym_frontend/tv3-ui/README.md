# Gym Management UI

This folder contains the HTML/CSS/JavaScript interface and its live API integration. `index.html` is the application connected to the Spring Boot backend. `demo.html` is the original local-only sample-data preview.

## Run the connected application

Start the backend first from `gym_backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

With the server running, serve this directory over HTTP on an allowed frontend origin. For example, in another terminal:

```powershell
cd gym_frontend/tv3-ui
python -m http.server 5173
```

Open `http://localhost:5173/`. Sign in with a real gym account. The default API URL is `http://localhost:8080/api`; set `window.GYM_API_BASE_URL` before `api.js` if the backend uses a different URL. Configure Supabase credentials only in the backend's ignored `.env` file.

All reads and writes in `index.html` go through backend APIs. The browser stores only the access token in `sessionStorage`; it never connects directly to Supabase. Backend responses and role checks remain authoritative. Member/trainer creation and account password reset return temporary passwords, which are shown once after success.

## Local-only preview

Open `demo.html` directly or serve the same directory and visit `/demo.html`. This page uses seeded records, simulated account selection and `localStorage`; it does not call Java or Supabase. Do not use it to verify real data changes.

## Files

- `index.html`: live application entry point.
- `api.js`: authenticated JSON API client and role-based data loading.
- `live-app.js`: API-connected screens, forms and interactions.
- `demo.html`, `app.js`, `core.js`: original local preview retained for comparison.
- `styles.css`: shared layout and styles.

The Admin/Staff interface loads members, plans, trainers, registrations, sessions, expirations and accounts from the backend. Member and Trainer accounts load their own portal through role-specific endpoints. The backend checks permissions and validates writes.

## Test scope

JavaScript syntax checks and backend automated tests can be run locally. A real Supabase end-to-end check must use the configured backend environment and approved test records. The demo validation results apply only to the local prototype and are not proof of API integration.
