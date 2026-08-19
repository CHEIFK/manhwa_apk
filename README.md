<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/c49de930-bd81-43b5-91df-4b976d357170

## CI/CD & Automated GitHub Actions Build

An automated GitHub Actions workflow is pre-configured at [`.github/workflows/build-android.yml`](.github/workflows/build-android.yml).

Every push to `main` or `master` (and pull requests) will automatically trigger a build in the cloud and generate the APK as a downloadable artifact.

### Configuring Signing in GitHub Repository Secrets

To produce a signed release APK automatically in GitHub Actions, configure the following secrets in your repository (**Settings** -> **Secrets and variables** -> **Actions**):

| Secret Name | Description | Example / Note |
|---|---|---|
| `KEYSTORE_BASE64` | Base64-encoded keystore file | Run `base64 -w 0 your-keystore.jks` and paste the output string |
| `STORE_PASSWORD` | Keystore password | Password used when creating keystore |
| `KEY_ALIAS` | Key alias name | e.g. `upload` or `key0` |
| `KEY_PASSWORD` | Key password | Password for the key (defaults to store password if identical) |
| `GEMINI_API_KEY` | *(Optional)* Gemini API Key | Automatically populated into `.env` |
| `GOOGLE_SERVICES_JSON` | *(Optional)* Firebase Config | Base content of `google-services.json` |

> **Note:** If no signing secrets are configured, GitHub Actions will still succeed and build an unsigned release APK artifact (`app-release-unsigned.apk`).

