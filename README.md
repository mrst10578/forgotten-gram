# Forgotten Gram

**Personal Telegram Archive Studio**: a small Android client dedicated to copying permitted Telegram channel media into channels you administer, with editable, template-generated captions.

## MVP functionality
- Sign in with your own Telegram API ID/hash, phone, login code and 2FA password.
- Browse your accessible channels and their recent media messages.
- Pick a destination channel; inspect and edit a generated caption.
- Confirm before sending a copy using TDLib `InputMessageForwarded` + `MessageCopyOptions` (no forward attribution).
- Check `MessageProperties.canBeCopied` and respect Telegram's content protection.
- Save caption template and archive destination locally.
- Show a local archived marker after TDLib confirms a sent message.

## Build
1. Open **Actions > Android debug APK > Run workflow**, or push to `main` / the feature branch.
2. Once the build succeeds, download the `forgotten-gram-debug-apk` artifact.
3. Install the APK, enter credentials **on the device** and authorize Telegram login.

**Status:** Source MVP only. Automated Android builds and live Telegram login are not yet validated until GitHub Actions completes. Do not treat it as production-ready.

## Security and limitations
- Never put your Telegram API hash, phone login code, 2FA password or TDLib session into a commit.
- The app's local TDLib database contains account material and must stay in private app storage.
- The TDLib binary is the community-built `io.github.tdlib-android:core:0.1.1` package. Its source/provenance should be audited before using an important account; it is not an official Telegram-built AAR.
- The first version supports **single permitted media messages**, not bulk copying, albums, protected media or text-only messages.
- No server or external automation service is needed. The Android app must be running during an operation.
- Default Android fonts are used unless a suitably licensed **Mikhak** font is supplied for bundling.

Official references: https://core.telegram.org/tdlib/getting-started and https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1message_copy_options.html
