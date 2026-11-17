# Nijatva Contacts

A fast, private contacts app for Android with a dark interface, groups, favourites and offline VCF import/export — plus an optional one-tap Zoho contacts import.

![minSdk](https://img.shields.io/badge/minSdk-21-blue) ![targetSdk](https://img.shields.io/badge/targetSdk-34-green) ![license](https://img.shields.io/badge/license-Apache--2.0-orange)

## Features

- **Contacts list** with photo avatars, coloured initials, fast search and A–Z sections
- **Favourites** tab for starred contacts
- **Groups** tab with contact counts; tap a group to filter
- **Sorting** by first or last name
- **VCF export / import** — back up all contacts to a file, or import from one
- **Optional Zoho sync** — sign in once (OAuth with PKCE) and your Zoho contacts are copied into the device
- **Appearance & Developer screens** — theme, accent colour and project links
- **Nijatva apps** — soft links to Gallery, Photos, Camera and Files
- **Dark interface** built for one-handed use
- Lightweight: about 55 KB APK, no third-party libraries

## Download

Grab the latest APK from the [Releases](../../releases) page.

## Build

Pure framework Java — no AndroidX, no third-party libraries.

```
./gradlew assembleRelease
```

Signing configuration is intentionally omitted — use your own keystore.

## Zoho sync (optional)

The public source ships with placeholder Zoho credentials. To enable the feature in your own build:

1. Create an OAuth client at <https://api-console.zoho.in> — type **Mobile-based Applications**
2. Authorized Redirect URI: `mycontacts://oauth`
3. Fill `CLIENT_ID` (and `CLIENT_SECRET` if you use one) in `ZohoConfig.java`

The app uses PKCE, so an empty client secret works for mobile clients. Never publish a client secret.

## The Nijatva family

The app carries the shared Nijatva signature: the Appearance and Developer screens come from the same source as the other Nijatva apps. `NijatvaMediaIntents` provides soft hand-offs to the sibling apps — every call is optional, so a missing app never crashes this one.

## Permissions

Contacts (read/write) and Internet (only for the optional Zoho sync).

## License

Apache License 2.0 — see [LICENSE](LICENSE).
