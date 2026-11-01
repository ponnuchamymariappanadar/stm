# Publishing to Maven Central

Releases are published to [Maven Central](https://central.sonatype.com) by the GitHub Actions
workflow `.github/workflows/release.yml` whenever a GitHub release is published. Once released,
anyone can use the library with:

```xml
<dependency>
    <groupId>io.github.ponnuchamymariappanadar</groupId>
    <artifactId>stm</artifactId>
    <version>0.1.0</version>
</dependency>
```

The Java packages stay `com.easysoft.stm`. The Maven `groupId` is `io.github.ponnuchamymariappanadar`
because Maven Central only accepts a group you can prove you own, and `io.github.<your-username>` is
proven automatically by your GitHub account. (Using `com.easysoft` would need DNS access to the
`easysoft.com` domain.)

## One-time setup

### 1. Create a Maven Central account

1. Go to https://central.sonatype.com and click **Sign In**, then sign up using **GitHub**.
2. Open **View Namespaces** (from your account menu). `io.github.ponnuchamymariappanadar` should be
   listed as **Verified**. If it shows "Unverified", click **Verify Namespace** and follow the
   instructions (they ask you to create a temporary public GitHub repository with a given name).

### 2. Create a publishing token

1. On central.sonatype.com, open your account menu → **View Account** → **Generate User Token**.
2. It shows a **username** and a **password** (both are random strings). Copy both somewhere safe
   for step 4; they are shown only once.

### 3. Create a GPG signing key

Maven Central requires every file to be signed.

1. Install **Gpg4win** from https://gpg4win.org (the default options are fine).
2. Open **Command Prompt** and run:

   ```
   gpg --full-generate-key
   ```

   Choose **RSA and RSA**, size **4096**, expiry **0** (does not expire) or **2y**, then enter your
   name, your email and a **passphrase**. Remember the passphrase.
3. Find the key ID (the long hex string after `rsa4096/`):

   ```
   gpg --list-secret-keys --keyid-format=long
   ```

4. Publish the public key so Maven Central can check signatures:

   ```
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   ```

5. Export the private key for GitHub:

   ```
   gpg --armor --export-secret-keys YOUR_KEY_ID > private-key.asc
   ```

   Open `private-key.asc` in Notepad; you will paste its whole content in the next step.
   **Delete the file afterwards** and never commit it.

### 4. Add the secrets to GitHub

On https://github.com/ponnuchamymariappanadar/stm go to **Settings → Secrets and variables →
Actions → New repository secret** and add these four:

| Name | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | token username from step 2 |
| `MAVEN_CENTRAL_PASSWORD` | token password from step 2 |
| `GPG_PRIVATE_KEY` | the entire content of `private-key.asc`, including the `-----BEGIN` / `-----END` lines |
| `MAVEN_GPG_PASSPHRASE` | the passphrase from step 3 |

## Releasing a version

1. Make sure `<version>` in `pom.xml` is the version you want (for example `0.1.0`) and that
   `CHANGELOG.md` has a section for it. Commit and push.
2. On GitHub: **Releases → Draft a new release**. Create a tag `v0.1.0` on `main`, set the title to
   `v0.1.0`, paste the CHANGELOG section as the notes, and click **Publish release**.
3. The **Release to Maven Central** workflow starts (see the **Actions** tab). It builds, runs the tests,
   signs the files and uploads them.
4. On central.sonatype.com open **Publish → Deployments**. When the deployment shows **Validated**,
   click **Publish**. After 15–30 minutes the library is available to everyone; search for it at
   https://central.sonatype.com.

For the next release, change `<version>` (for example to `0.2.0`), update the CHANGELOG, and repeat.
A version that has been published to Maven Central can never be changed or deleted.

## If the workflow fails

Open the failed run in the **Actions** tab and look at the **Build, sign and upload** step:

- `401` or `Unauthorized`: the token secrets are wrong or expired. Generate a new token (step 2).
- `gpg: signing failed` or `no secret key`: the `GPG_PRIVATE_KEY` or passphrase secret is wrong.
- Validation errors on central.sonatype.com about the signature: the public key has not reached the
  key server yet. Wait an hour, or run the `--send-keys` command again, then re-run the workflow
  (**Actions → Release to Maven Central → Run workflow**).
