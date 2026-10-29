<div align="center">
  <img src="./assets/design/app_logo.svg" width="300" height="100" />
  <h1>RAmpMusic</h1>
  <p><b>RAmpMusic</b> is a powerful, <b>multilingual</b> Android application for streaming music from YouTube Music.</p>
  <p>Based on <b>RiMusic</b> (by fast4x) and extending the original foundation of <b>ViMusic</b> (by vfsfitvnm), enhanced with advanced streaming engine fixes, robust continuous playback, and modern optimizations.</p>
</div>

---

## 🚀 What's New in RAmpMusic (Key Enhancements)

- **Continuous Playback Engine**: Resolved the notorious 28–30s stream cutoff (`InvalidResponseCodeException` / HTTP 403) by introducing prioritized multi-client resolution (leveraging verified Apple Vision Pro / `VISIONOS` streams for unrestricted full-range chunk delivery).
- **Automated PO Token Generation**: Integrated on-device headless WebView Botguard challenge resolution to safely generate Proof-of-Origin (PO) tokens for streaming requests.
- **Optimized Caching & Streaming**: Upgraded ExoPlayer data source pipelines to prevent sub-range cache fragmentation, allowing seamless audio streaming and offline persistence.
- **Stream Playability Pre-Validation**: Proactive 512 KB chunk range verification before playback, ensuring smooth audio transitions.

---

## ✨ Features

- **Multilingual support:** English, Spanish, Italian, German, Russian, French, Czech, Turkish, Romanian, Portuguese, and many more.
- **UI Customization:** Switch between modern RAmpMusic style, RiMusic style, and classic ViMusic appearance.
- **Stream from YouTube Music:** High-quality audio streaming with adaptive format selection (Opus / AAC).
- **Offline Playback & Auto Cache:** Custom cache size with seamless background caching for offline playback.
- **Song & Playlist Downloader:** Download individual songs or entire playlists directly to device storage.
- **Background Playback:** Continuous background playback with customizable task-manager swipe actions.
- **Audio Visualizer:** Multiple visualizer effects on the player (requires optional microphone permission).
- **Synchronized Lyrics:** Fetch, display, edit, and translate synchronized (LRC) and unsynchronized lyrics.
- **Local & Online Playlists:** Full management of local playlists, with import/export capabilities.
- **Queue Management:** Persistent queue, reordering, and quick-clear controls.
- **Listening Statistics:** Track your favorite tracks, artists, and playback history.
- **Audio Controls:** Equalizer, audio normalization, silence skipping, and customizable sleep timer.
- **Themes:** Dynamic Material You, Light, Dark, PureBlack (OLED), and ModernBack themes.
- **Broad Device Support:** Android Auto and Android TV compatible.

---

## 📷 Screenshots

<div align="center">
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="15%" />
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" width="15%" />
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg" width="15%" />
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpg" width="15%" />
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/5.jpg" width="15%" />
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/6.jpg" width="15%" />
</div>

---

## 🛠️ Building from Source

### Prerequisites:
- Android Studio / Android SDK (target SDK 34, min SDK 21)
- JDK 17 or JDK 18
- Gradle (handled via Gradle wrapper `./gradlew`)

### Build Debug APK:
```bash
./gradlew assembleDebug
```
The resulting APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🌐 Languages Available

- **Afrikaans:** thanks to [HelloZebra1133](https://crowdin.com/profile/HelloZebra1133)
- **Arabic:** thanks to [ABS zarzis](https://crowdin.com/profile/abszar) and [Ahmad Al Juwaisri](https://crowdin.com/profile/juwaisri)
- **Bashkir:** thanks to [Shilave malay](https://crowdin.com/profile/Bash.boy)
- **Catalan:** thanks to [EMC_Translator](https://crowdin.com/profile/EMC_Translator)
- **Chinese Simplified & Traditional:** thanks to [SharkChan0622](https://github.com/SharkChan0622)
- **Czech:** thanks to [ikanakova](https://github.com/ikanakova) and [JZITNIK-github](https://github.com/JZITNIK-github)
- **Dutch:** thanks to [BabyBenefactor](https://crowdin.com/profile/BabyBenefactor)
- **English:** thanks to [fast4x](https://github.com/fast4x)
- **Esperanto:** thanks to [kjev666](https://github.com/kjev666)
- **Finnish:** thanks to [rikalaj](https://crowdin.com/profile/rikalaj)
- **French:** thanks to [NEVARLeVrai](https://github.com/NEVARLeVrai)
- **German:** thanks to [OlimitLolli](https://crowdin.com/profile/OlimitLolli), [Lolozweipunktnull](https://crowdin.com/profile/Lolozweipunktnull), [Seryoga1984](https://crowdin.com/profile/Seryoga1984), [Conk](https://crowdin.com/profile/conk) and [siggi1984](https://github.com/siggi1984)
- **Greek:** thanks to [Marinkas](https://github.com/Marinkas)
- **Hebrew:** thanks to [TheCreeperDuck](https://crowdin.com/profile/thecreeperduck)
- **Hindi:** thanks to [Th3-C0der](https://github.com/Th3-C0der) and [Sharunkumar](https://crowdin.com/profile/sharunkumar)
- **Hungarian:** thanks to [Zan1456](https://crowdin.com/profile/Zan1456) and [Ndvok](https://crowdin.com/profile/ndvok)
- **Italian:** thanks to [CiccioDerole](https://crowdin.com/profile/CiccioDerole) and [fast4x](https://github.com/fast4x)
- **Indonesian:** thanks to [teddysulaimanGL](https://github.com/teddysulaimanGL) and [Fausta Ahmad](https://crowdin.com/profile/faustaahmad)  
- **Japanese:** thanks to [Mid_Vur_Shaan](https://crowdin.com/profile/Mid_Vur_Shaan)
- **Korean:** thanks to [ZeroZero00](https://crowdin.com/profile/ZeroZero00) and [TsyQax](https://crowdin.com/profile/TsyQax)
- **Odia:** thanks to [Get100percent](https://github.com/Get100percent)
- **Persian:** thanks to [CUMOON](https://github.com/CUMOON)
- **Polish:** thanks to [AntoniNowak](https://crowdin.com/profile/AntoniNowak), [roklc](https://github.com/roklc) and [Adam Kop](https://crowdin.com/profile/damianadam000)
- **Portuguese (PT & BR):** thanks to [ManuelCoimbra](https://crowdin.com/profile/ManuelCoimbra), [RegularWater](https://crowdin.com/profile/RegularWater) and [NEVARLeVrai](https://github.com/NEVARLeVrai)
- **Romanian:** thanks to [OrangeZXZ](https://github.com/OrangeZxZ)
- **Russian:** thanks to [Nebula-Mechanica](https://crowdin.com/profile/Nebula-Mechanica), [Alnoer](https://crowdin.com/profile/Alnoer), [kptmx](https://crowdin.com/profile/kptmx), [Crayz310](https://github.com/Crayz310), [Vladimir](https://crowdin.com/profile/vladimir15426) and [siggi1984](https://github.com/siggi1984)
- **Spanish:** thanks to [Corotyest](https://crowdin.com/profile/corotyest), [DanielSevillano](https://github.com/DanielSevillano) and [Fabian Urra](https://crowdin.com/profile/nsagcl)
- **Sinhala:** thanks to [VINULA2007](https://crowdin.com/profile/VINULA2007)
- **Turkish:** thanks to [abfreeman](https://github.com/abfreeman), [2010furs](https://github.com/2010furs) and [Muha Aliss](https://crowdin.com/profile/muhaaliss)
- **Ukrainian:** thanks to [Crayz310](https://github.com/Crayz310) 
- **Vietnamese:** thanks to [teaminh](https://crowdin.com/profile/teaminh)

---

## 🫂 Acknowledgments & Credits

RAmpMusic is built on top of the remarkable work created by open-source contributors:

- [**RiMusic**](https://github.com/fast4x/RiMusic) by [@fast4x](https://github.com/fast4x): The core project upon which RAmpMusic is based.
- [**ViMusic**](https://github.com/vfsfitvnm/ViMusic) by [@vfsfitvnm](https://github.com/vfsfitvnm): The original foundational music player.
- [**YouTube-Internal-Clients**](https://github.com/zerodytrash/YouTube-Internal-Clients): InnerTube client research and API schemas.
- [**Ionicons**](https://github.com/ionic-team/ionicons): Premium hand-crafted icons.
- **Logo & Design**: [@ikanakova](https://github.com/ikanakova).
- [**Rubik** Font](https://fonts.google.com/specimen/Rubik): Default application typography.
- [**Poppins** Font](https://fonts.google.com/specimen/Poppins): Classic alternative typography.
- [**Translator**](https://github.com/therealbush/translator): Google Translate library for JVM.

### Original RiMusic Developers & Designers:
- [25huizengek1](https://github.com/25huizengek1)
- [Craeckie](https://github.com/Craeckie)
- [DanielSevillano](https://github.com/DanielSevillano)
- [fast4x](https://github.com/fast4x)
- [ikanakova](https://github.com/ikanakova)
- [JZITNIK-github](https://github.com/JZITNIK-github)
- [locxter](https://github.com/locxter)
- [roklc](https://github.com/roklc)
- [sharunkumar](https://github.com/sharunkumar/sharunkumar)
- [SuhasDissa](https://github.com/SuhasDissa)

---

## ❗ Disclaimer

This project and its contents are not affiliated with, funded, authorized, endorsed by, or in any way associated with YouTube, Google LLC, or any of their affiliates and subsidiaries.

Any trademark, service mark, trade name, or other intellectual property rights used in this project are owned by their respective owners.
