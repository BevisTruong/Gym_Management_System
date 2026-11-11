# Gym UI prototype — TV3

This folder contains the clean interface revision delivered by TV3. The four application files are byte-for-byte copies of the delivered Clean UI package. This source was written in HTML, CSS and JavaScript; it is not an Anima export.

## Preview

Open `index.html` in a modern browser, or serve this directory over HTTP. Keep all four application files together. No package installation or build is required for the local prototype.

Choose an account and click **Continue**. If **Set your password** appears, use `demo12345` in both fields. Password values are discarded; the form only simulates an interface state.

Open **Help → About this preview** for **Preview date and time** and **Restore sample data**. The initial sample time is 9 November 2026, 09:00. Local changes are stored in this browser, not on a shared server.

## Files

- `index.html`: application entry point.
- `styles.css`: shared layout and styling.
- `app.js`: screens, events and local sample-state interactions.
- `core.js`: sample records and prototype business rules.

## TV4 integration

See `../../docs/ui/TV4_Handoff_VI.md` for integration guidance and proposed API mappings. Check these proposals against the current backend before implementing them.

For Spring Boot static hosting, TV4 can copy the four application files into the chosen static-resource directory while retaining relative paths. Real integration also requires replacing local-data operations with API calls, implementing server authentication/authorization and enforcing business rules on the server.

The prototype does not authenticate users, call Java business APIs or connect to a database. Importing it into Figma does not add those capabilities.

## Recorded validation

The source Clean UI package records 61/61 JavaScript business-rule checks and 22/22 browser interaction checks. These results apply to the delivered local prototype, not to a future integrated backend. Full evidence remains in `TV3_Gym_Clean_UI.zip`.
