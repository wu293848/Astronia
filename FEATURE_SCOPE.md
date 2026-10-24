# Astronia IPTV build — requested scope

Base: upstream Astronia 1.0.7 (GPL-3.0), preserved under `upstream/Astronia`.

Requested behavior:
- Multiple saved M3U and Xtream Codes provider profiles.
- Live TV playback without EPG/TV guide or channel logos.
- Xtream VOD/movie categories and playback.
- Xtream series categories, series detail, season/episode selection, playback.
- Keep Astronia's Material 3 player/UI and no bundled streams.

Initial implementation files added:
- `app/src/main/java/com/antoniegil/astronia/data/xtream/XtreamModels.kt`
- `app/src/main/java/com/antoniegil/astronia/data/xtream/XtreamApi.kt`
- `app/src/main/java/com/antoniegil/astronia/data/xtream/ProviderStore.kt`

These files establish the Xtream API/data and encrypted local profile storage layer. They are not yet wired into HomePage/navigation, and EPG/logo UI removal plus full movie/series browsing still need implementation. Do not treat this as a finished APK yet.

Cloud build workflow: `.github/workflows/build-iptv-apk.yml` builds debug APK artifacts with GitHub Actions once this fork is pushed to a repository the user controls.
