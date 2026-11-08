---
name: fdroid-release
description: Release a new version of Misty Steps to the Potloodgum F-Droid repo (fdroid.potloodgum.com). Use when the user asks to release, publish, ship or push an update of the app, make a new version, or bump the version number.
---

# Releasing Misty Steps on F-Droid

Misty Steps is published in Tjerk's own F-Droid repo, **https://fdroid.potloodgum.com/repo**. All the
work is done by `tools/release.sh`; this skill is about doing it in the right order and writing a
good changelog. Releasing is public: people get the update on their phones.

## 1. What changed since the last release?

```sh
git describe --tags --abbrev=0                 # last release tag, e.g. v1.0.1
git log <last-tag>..HEAD --oneline
```

Sort the changes into what a user notices (new features, fixes, things that look or feel different)
and what they don't (refactors, tooling, docs). If nothing user-facing changed, say so and ask
whether a release is still wanted.

## 2. Pick the version bump

- **patch**: fixes and small tweaks (1.2.3 → 1.2.4)
- **minor**: new features (1.2.3 → 1.3.0)
- **major**: big changes, e.g. how data is stored or what the app is (1.2.3 → 2.0.0)

Suggest one with a one-line reason; ask the user if it isn't obvious.

## 3. Write the changelog

The next versionCode is `versionCode` in `version.properties` plus 1. Write
`fastlane/metadata/android/en-US/changelogs/<next versionCode>.txt`:

- For users, in plain English: what they'll notice, not how it was built.
- Short lines starting with "- ", newest and most noticeable first. Maximum **500 characters**
  (F-Droid's limit; the script checks).
- No internal names (classes, files, "refactor", "SpeedGate").

Show it to the user and let them adjust it.

## 4. Commit everything else

`tools/release.sh` refuses to run with uncommitted changes, apart from that changelog. Commit
pending work first (normal commit rules: only when the user wants it committed).

## 5. Dry run

```sh
tools/release.sh <patch|minor|major> --dry-run
```

It checks the branch, the keys and the live repo, then runs the unit tests, lint and a signed
release build, verifies the APK (app id, version, not debuggable, signed with the Potloodgum release
key), and builds the repo, without deploying. If anything fails, **fix the cause**. Never work around a
check, edit `version.properties` by hand, or reuse a versionCode.

## 6. Release

Confirm with the user before this step, because it publishes. Then:

```sh
tools/release.sh <patch|minor|major>
```

It bumps the version, builds and verifies again, updates and signs the repo index, deploys to
Cloudflare, checks the new version is live, then commits, tags `v<version>` and pushes.

If it stops **after** "Deploying", the release may already be live: check
https://fdroid.potloodgum.com/repo/index-v2.json before re-running anything.

## 7. Afterwards

- Tell the user the release is live, and that on their phone, pulling down to refresh in F-Droid (or
  Droid-ify) shows the update. Their local test build is a separate app, "Misty Steps Dev".
- Add a line to the "Releases" list in `workflow.md` (version, date, one-line summary) and commit it.

## Good to know

- **Keys (never commit, never print):** app key `~/Keystores/potloodgum/mistysteps-release.jks`
  (passwords in `keystore.properties`), repo key `~/Keystores/potloodgum/fdroid-repo.p12`
  (password in `fdroid/config.yml`).
- **Store texts, icon and screenshots** are in `fastlane/metadata/android/en-US/`; the release picks
  up any change there. The listing settings (donate link, license, categories) are in
  `fdroid/metadata/com.potloodgum.mistysteps.yml`.
- **Tools:** `fdroid` comes from `brew install fdroidserver`, `cf` is the Cloudflare CLI. If `cf deploy`
  says you're not signed in, the user runs `cf auth login`.
- **Hosting:** the repo is a static Cloudflare Worker, `potloodgum-fdroid` (config in `fdroid/site/`).
