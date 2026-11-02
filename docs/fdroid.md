# F-Droid

Awakr is distributed through two F-Droid channels:

1. **Our own repository** – live now, worked on by the project, no review needed.
2. **The official f-droid.org catalogue** – needs a merge request against `fdroiddata`; see
   [Inclusion in f-droid.org](#inclusion-in-fdroiddotorg).

## Self-hosted repository

| | |
|---|---|
| Repository URL | `https://caendeith.github.io/awakr/fdroid/repo` |
| Repository name | Awakr |
| Signing key fingerprint (SHA-256 of the index certificate) | `09 B3 D4 E7 27 89 22 EC F8 3E 5B 8B 02 13 99 63 9D F3 E4 AD 83 E0 5A 5C 3F F1 C1 C7 95 E3 3D 01` |
| Served from | `docs/fdroid/repo` on `main` (GitHub Pages, `main` → `/docs`) |

### Adding it in the F-Droid client

1. Install the F-Droid client from <https://f-droid.org>.
2. **Settings → Repositories → + / Add repository** (Настройки → Репозитории → Добавить репозиторий).
3. Paste `https://caendeith.github.io/awakr/fdroid/repo`, confirm. The client shows the fingerprint –
   it must match the value in the table above.
4. Pull to refresh, search for **Awakr**, install.

Updates are then offered by F-Droid like for any other app. Nothing else is required: the repository
serves the signed index, the APK and the icons, and the app has no internet permission of its own.

### What is inside `docs/fdroid/repo`

| File | Purpose |
|---|---|
| `index-v1.jar`, `index-v1.json` | legacy index (SHA-1 signed, expected for this format) |
| `index-v2.json`, `entry.jar`, `entry.json` | modern index used by F-Droid 1.16+; `entry.jar` is SHA-384/SHA-256 signed |
| `index.xml`, `index.jar`, `index.html`, `index.css`, `index.png` | old-style index and a plain HTML listing |
| `Awakr-<version>.apk` | the release APK, same file as on GitHub Releases |
| `icons/`, `icons-<density>/` | extracted launcher icons |

`index-v1.jar`/ `index.jar` are signed with SHA-1 on purpose – fdroidserver signs the legacy format
that way and old clients need it. The file F-Droid actually verifies today is `entry.jar`
(`SHA384withRSA`, `SHA-256` digest).

### Key material and backups

The repository signing key lives **outside the repository**:

- `tools/fdroid/keystore.p12` – PKCS12, alias `awakr-repo`, RSA 4096
- `tools/fdroid-repo-password.txt` – its password (file mode, git-ignored together with `tools/`)
- `tools/fdroid/config.yml` – fdroidserver configuration (SDK paths, repo name, key alias)

Losing the keystore means every user has to remove and re-add the repository, because the fingerprint
is pinned on the client side. Back both files up somewhere private.

### Publishing a new version

1. Build and sign the release APK (`./gradlew :app:assembleRelease`, see `CONTRIBUTING.md`), tag the
   commit, upload the APK to GitHub Releases.
2. Copy it into the repository folder:

   ```bash
   cp app/build/outputs/apk/release/app-release.apk tools/fdroid/repo/Awakr-<version>.apk
   ```

3. Regenerate and re-sign the index (needs JDK `bin` on `PATH`, otherwise fdroidserver cannot run
   `jar`):

   ```bash
   export PATH="/c/Program Files/Java/jdk-17/bin:$PATH"
   cd tools/fdroid && ./../fdvenv/Scripts/fdroid.exe update
   ```

   `fdroid update` may end with a harmless `FileNotFoundError` for `repo/status/*.json` on Windows
   (a path bug in fdroidserver when the launcher path contains a drive letter); the index files are
   already written at that point. Verify with
   `jarsigner -verify tools/fdroid/repo/entry.jar`.
4. Copy the generated repository into the Pages tree and commit:

   ```bash
   rsync -a --delete tools/fdroid/repo/ docs/fdroid/repo/
   git add docs/fdroid/repo && git commit -m "fdroid: publish <version>"
   ```

5. Check <https://caendeith.github.io/awakr/fdroid/repo/index-v2.json> and refresh the F-Droid client.

`config.yml` keeps `archive_older: 0`, so the index only lists the newest release; older APKs are
dropped from the repository folder.

## Inclusion in f-droid.org

The official catalogue does not host our repository: it builds every app from source. The request is a
merge request against [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) (GitLab, not GitHub) that
adds `metadata/io.github.caendeith.awakr.yml`.

A ready-to-submit draft is kept in [`fdroid/io.github.caendeith.awakr.yml`](../fdroid/io.github.caendeith.awakr.yml):

```yaml
Categories: Alarm Clock
License: MIT
AuthorName: caendeith
WebSite: https://caendeith.github.io/awakr/
SourceCode: https://github.com/caendeith/awakr
IssueTracker: https://github.com/caendeith/awakr/issues
Changelog: https://github.com/caendeith/awakr/releases
AutoName: Awakr
RepoType: git
Repo: https://github.com/caendeith/awakr.git
Builds:
  - versionName: 1.3.0
    versionCode: 6
    commit: v1.3.0
    gradle:
      - 'yes'
    output: app/build/outputs/apk/release/app-release.apk
```

What the F-Droid build server expects:

- the commit must be reachable from a **tag** (their update check uses tags), and `versionCode` must
  match `app/build.gradle.kts`;
- `gradle: - yes` runs `assembleRelease`; flavours would add their names below that key;
- the build has to work with the official Google/Maven Central repositories – that is exactly what the
  default `settings.gradle.kts` does, the Chinese mirrors are opt-in via `AWAKR_MIRRORS=true`;
- all dependencies must be free software and fetched from public repositories (they are: androidx,
  Compose, Room, CameraX, ZXing, DataStore, WorkManager, JUnit);
- `enableAntiFeatures` is not needed: no ads, no tracking, no `INTERNET` permission at all;
- metadata text (title, descriptions, changelogs, screenshots) comes from the
  `fastlane/metadata/android/<locale>/` tree in this repository, so keep `changelogs/<versionCode>.txt`
  up to date before each release.

The merge request can only be opened by the account that owns the fork, so it is a manual step for the
maintainer.

### Click by click (GitLab web UI)

1. Fork: open <https://gitlab.com/fdroid/fdroiddata/-/forks/new>, pick your namespace, name the project
   `fdroiddata`, visibility *Public*, press **Fork project**. Wait a minute for the fork to finish.
2. Open your fork: `https://gitlab.com/<your-user>/fdroiddata`.
3. **Code → branches → New branch**, name it `io.github.caendeith.awakr`, create from `master`.
4. With that branch selected press **+ → New file**, path
   `metadata/io.github.caendeith.awakr.yml`, and paste the YAML block above.
5. Commit message `New app: io.github.caendeith.awakr`, keep *Commit to branch
   io.github.caendeith.awakr*, tick **Start a new merge request**.
6. On the merge request form keep the target `fdroid/fdroiddata` `master`, title
   `New app: Awakr (io.github.caendeith.awakr)`, and use the description template below.
7. Submit. Their CI runs `fdroid lint` (metadata schema, fastlane, screenshots) and a test build; fix
   anything reported in red before asking for review.
8. Answer the reviewers. After the merge the app is built on their server and shows up on
   <https://f-droid.org> with the next index update (usually a few days).

### Merge request description

```text
Awakr is an alarm clock that makes you solve a mission before it lets you out of bed: math, a QR or
barcode you stick in the bathroom, shaking the phone, or typing a phrase. Alarmy asks 100 EUR a year
for that; Awakr is MIT, has no ads, no subscription, no account and no INTERNET permission at all, so
nothing can leave the device.

Why it is a good fit for F-Droid:
- no network permission, no proprietary SDKs (Compose, Room, DataStore, WorkManager, CameraX, ZXing),
- no ads, no tracking, no analytics, no crash reporting, no anti-features,
- 100% free software, MIT, source and releases on GitHub,
- missions live in separate Gradle modules behind a public plugin API, so the core has no idea what
  a mission is.

Build notes:
- the repository builds with `./gradlew assembleRelease` straight from the tag, it does not need a
  signing config (the release type stays unsigned when `keystore.properties` is absent),
- dependencies come from Google Maven and Maven Central; regional mirrors are opt-in and off by
  default,
- `app/build.gradle.kts` pins compileSdk/targetSdk 36, minSdk 26 and build-tools 36.0.0.

Upstream: https://github.com/caendeith/awakr
```

Until that merge request is merged, updates for users come from GitHub Releases and from our own
repository described above.

### One thing to warn users about

F-Droid builds the app on its own server and signs it with **its** key, so an APK from f-droid.org
cannot be installed over the one from our repository or from GitHub Releases (and the other way
round). Pick one source per device; switching means uninstalling first, which deletes the alarms.
