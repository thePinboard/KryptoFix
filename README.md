<div align="center">

# 🛡️ KryptoFix

**A local-first, encrypted password manager for Android.**
No cloud. No account. No tracking. No network access.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-6D4AFF)
![Language](https://img.shields.io/badge/language-Java-3997FF)
![Dependencies](https://img.shields.io/badge/dependencies-none-47D7A0)
![Permissions](https://img.shields.io/badge/INTERNET%20permission-none-47D7A0)

</div>

---

## Overview

KryptoFix keeps all of your credentials on your own device. You choose a single master password; from it an encryption key is derived, and every login you store (title, user name, URL, password and notes) is saved in **one encrypted vault file** inside the app's private storage.

The app does not request the Android `INTERNET` permission, so it is technically unable to send your data anywhere. The entire application consists of two Java classes and uses nothing but the Android framework and the JDK's standard cryptography.

> **Status:** Version 1.0.0 is a compact *Core Edition*. It has not been independently security-audited. Please evaluate it with non-critical data first and keep your own backup of important credentials.

## Features

- 🔐 **Encrypted vault** – AES-256-GCM with a PBKDF2-HMAC-SHA256 derived key (600,000 iterations)
- 🗂️ **Entry management** – create, edit and delete logins with title, user name, URL, password and notes
- 🔎 **Instant search** – across title, user name, URL and notes
- 🎲 **Password generator** – 20 characters from four character classes via `SecureRandom`
- 📊 **Security check** – detects weak passwords, reused passwords and entries without a password (fully offline)
- 💪 **Strength indicator** – live feedback while typing, always shown as text + symbol, not by colour alone
- 📋 **Clipboard protection** – copied passwords are flagged as sensitive and auto-cleared (15 / 30 / 60 / 120 s)
- 🔒 **Auto-lock** – the vault locks immediately when the app leaves the foreground
- 🙈 **Screen protection** – screenshots, screen recording and the task-switcher preview are blocked (`FLAG_SECURE`)
- 🔁 **Change master password** – re-encrypts the vault with a fresh random salt
- 📴 **Offline by design** – no `INTERNET` permission, no cloud backup, no telemetry, no ads

## Security design

| Component | Choice |
|---|---|
| Key derivation | `PBKDF2WithHmacSHA256`, 600,000 iterations, 16-byte random salt, 256-bit key |
| Encryption | `AES/GCM/NoPadding`, 128-bit authentication tag |
| Nonce (IV) | 12 random bytes, freshly generated on **every** save |
| Associated data | First 24 header bytes (magic, iteration count, salt) are authenticated |
| Randomness | `java.security.SecureRandom` |
| Persistence | Atomic write: temp file → `fsync` → rename |
| Backups | `android:allowBackup="false"` |
| Network | No `INTERNET` permission, cleartext traffic disabled |

KryptoFix implements **no custom cryptographic primitives**; it relies exclusively on the platform's JCA/JCE providers.

### Vault file format (`KFX1`)

```
offset  size  field
0       4     magic "KFX1"
4       4     PBKDF2 iteration count (int32, big-endian)
8       16    salt
24      12    AES-GCM IV (nonce)
36      n     ciphertext + 16-byte GCM tag
```

The encrypted payload is a UTF-8 JSON array with one object per entry. Bytes 0–23 are passed to AES-GCM as additional authenticated data, so manipulating the header makes decryption fail.

## Screens

| Screen | Purpose |
|---|---|
| **Setup** | Create the vault: choose and confirm a master password (min. 10 characters) |
| **Unlock** | Enter the master password to decrypt the vault |
| **Vault** | Alphabetical list of all entries, quick-add button |
| **Search** | Live full-text search |
| **Check** | Weak / reused / empty password analysis |
| **Settings** | Clipboard timeout, lock now, change master password, delete vault |
| **Editor** | Edit an entry, show/hide and generate passwords, copy user name / password |

## Not included yet

KryptoFix 1.0.0 deliberately focuses on a small, auditable core. The following features are **not** part of this release:

- [ ] KDBX (KeePass) compatibility
- [ ] Biometric unlock (Android Keystore + `BiometricPrompt`)
- [ ] Android Autofill service
- [ ] Encrypted import / export
- [ ] Configurable auto-lock timer
- [ ] Automated test suite

## Installation

1. Download the latest `KryptoFix-1.0.0.apk` from above, in the list, there is the .apk file for android 8 and newer.
2. Optionally verify the checksum:
   ```bash
   sha256sum KryptoFix-1.0.0.apk
   # 7580d35bad3ce0fec9e40e2c9aae5300c3fc9064fbee700f75f130cf2720a58c
   ```
3. On your device, allow installation from unknown sources for your browser or file manager.
4. Open the APK and install it.

> **Note:** If you build the APK yourself, the checksum and the signing certificate will differ from the published release. Android only accepts updates that are signed with the same key as the installed app.

## Build from source

KryptoFix is built with the classic Android command-line toolchain – no Gradle required.

### Requirements

- JDK 17 or newer
- Android build tools: `aapt`, `dx` (or `d8`), `zipalign`, `apksigner`
- `android.jar` of Android API 34 (from the Android SDK, `platforms;android-34`)

On Debian / Ubuntu the tools can be installed with:

```bash
sudo apt install openjdk-21-jdk-headless aapt dalvik-exchange apksigner zipalign
```

### Steps

```bash
ANDROID_JAR=/path/to/android-34/android.jar
mkdir -p build/classes build/gen

# 1. Compile resources and the manifest
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I "$ANDROID_JAR" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 1 --version-name 1.0.0 -F build/res.ap_

# 2. Compile the Java sources
javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR" \
  -d build/classes src/de/kryptofix/passwordmanager/*.java

# 3. Convert to DEX
dx --dex --min-sdk-version=26 --output=build/classes.dex build/classes

# 4. Assemble and align the APK
cp build/res.ap_ build/unsigned.apk
(cd build && aapt add unsigned.apk classes.dex)   # DEX must sit at the APK root
zipalign -f -p 4 build/unsigned.apk build/aligned.apk

# 5. Create a signing key (once) and sign
keytool -genkeypair -keystore my-release.jks -storetype PKCS12 -alias kryptofix \
  -keyalg RSA -keysize 4096 -validity 9125
apksigner sign --ks my-release.jks --ks-key-alias kryptofix \
  --out KryptoFix-1.0.0.apk build/aligned.apk

# 6. Verify
apksigner verify --verbose KryptoFix-1.0.0.apk
```

Keep your signing key safe and never commit it to the repository.

## Project structure

```
.
├── AndroidManifest.xml
├── res/
│   ├── mipmap-xxxhdpi/ic_launcher.png
│   └── values/strings.xml
└── src/de/kryptofix/passwordmanager/
    ├── MainActivity.java   # Programmatic UI, lifecycle, clipboard, screen protection
    └── Vault.java          # Entry model, PBKDF2 + AES-GCM, file I/O, strength heuristic, generator
```

A complete project documentation including the full annotated source code is available as a PDF: [`docs/KryptoFix_Project_Documentation.pdf`](docs/KryptoFix_Project_Documentation.pdf).

## Known limitations

- **Memory:** Decrypted entries are held as Java `String`s while the vault is unlocked and cannot be reliably wiped. The vault is locked and all references are dropped as soon as the app is stopped.
- **Hardware protection:** The key is derived from the master password only and is not additionally bound to the Android Keystore. Protection against offline attacks relies on a strong master password and the PBKDF2 cost.
- **Clipboard clearing** is timer-based inside the app process. If Android terminates the process before the timeout, the clipboard is not cleared.
- **No backup or sync:** Uninstalling the app or clearing its data permanently deletes the vault.
- **Master password:** It cannot be recovered or reset – by design.

## Security

If you discover a vulnerability, please report it privately through GitHub's **Security → Report a vulnerability** feature (or contact the author directly) instead of opening a public issue.

## Contributing

Issues and pull requests are welcome. For larger changes, please open an issue first to discuss what you would like to change. Please keep the project's principles in mind: no third-party dependencies without strong justification, no network access, and no custom cryptography.

## Disclaimer

This software is provided "as is", without warranty of any kind. You are responsible for keeping backups of your credentials and for choosing a strong master password.

## License

See the [LICENSE](LICENSE) file for details.

## Author

**App created by Daniel Kiebler**
