# Higgsfield watch designs

`concepts.png` is the generated design reference, not a screenshot of the apps.
`provenance.json` records the four Higgsfield jobs, prompts and production asset URLs.
The three unmodified generated background assets are bundled in
`wear/app/src/main/assets/providers/` as `noodle.png`, `squint.png`, and `stretch.png`.

The app decodes each asset at 512 × 512 and draws it beneath native Android controls.
No credential, runtime status, interactive text or control is baked into the artwork.
The three apps use one layout in a 228-unit square: name, status, task card, then three round actions.
Each control stays inside the round screen, and each touch target is 48dp or larger.
`WatchUi.java` holds the colours, the corner radius and the controls that all screens of an app share.
A gradient darkens the artwork behind the name and the actions.

Screenshots: `wear/screenshots/providers/*-higgsfield.png`. They show the layout before the shared layout
and must be taken again with the UI check.
UI check: `python3 tools/test-provider-watch-ui.py --serial emulator-5580`.

Settings and list icons use Google Material Icons (Apache 2.0; license alongside this file):
- https://github.com/google/material-design-icons/blob/master/src/action/settings/materialiconsoutlined/24px.svg
- https://github.com/google/material-design-icons/blob/master/src/action/list/materialiconsoutlined/24px.svg
