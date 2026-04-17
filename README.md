# Simple Music Player – Ad-Free Music Player

Clean, ad-free Android music player. AMOLED black theme, playlist support, no ads.

## Features
- Scans all audio files automatically (sorted newest-first)
- Songs tab + Playlists tab
- Live search (title / artist)
- Full-screen player with rotating album art disc
- Background playback with notification controls
- Shuffle, Repeat-All, Repeat-One
- Mini player bar always visible
- Zero ads, zero tracking

<img width="360" height="738" alt="Simple Music Player UI" src="https://github.com/user-attachments/assets/1da14a74-d20d-497c-af2c-2117bee638ac" />

---

## How to Build

1. Extract the zip
2. Open `MuzioLite/` in **Android Studio** (Hedgehog 2023.1.1 or newer)
3. Let Gradle sync finish (first run downloads ~200 MB)
4. **Build → Build Bundle(s)/APK(s) → Build APK(s)**
5. APK path: `app/build/outputs/apk/debug/app-debug.apk`

For a signed release APK (needed to install without developer mode on some devices):
- **Build → Generate Signed Bundle/APK → APK**
- Create or select a keystore, fill in the details, click Finish

---

## How playlists work
| Action | How to do it |
|---|---|
| Create playlist | Go to **Playlists** tab → tap **+** |
| Rename / Delete | Long-press a playlist |
| Add song to playlist | Long-press any song → pick a playlist |
| Remove song | Open playlist → long-press the song |

---

## Customise
- **Accent colour** — `res/values/colors.xml` → change `purple_primary`
- **App name** — `res/values/strings.xml` → change `app_name`
