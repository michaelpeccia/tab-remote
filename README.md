# Tab Remote: setup notes

An offline IR remote app for the Galaxy Tab 4 7.0 (SM-T230NU).
Controls: Samsung TV, Vizio TV, Samsung Blu-ray, Roku. All codes are built in, so it never needs internet.

---

## 1. Put it on GitHub (one time)

1. On github.com, click **New repository**.
   Name it `tab-remote`. Make it **Public** so the APK download link works without signing in.
   Don't add a README (this folder already has one). Click **Create repository**.
2. Extract `TabRemote.zip` into `C:\Users\micha\Downloads`. You should end up with a
   `Downloads\TabRemote` folder that has `app`, `README.md` and `build.gradle` directly inside it
   (if Windows makes `TabRemote\TabRemote`, move the inner folder up one level).
3. Open PowerShell and run:

```powershell
cd $HOME\Downloads\TabRemote
git init
git add .
git commit -m "Tab Remote v1"
git branch -M main
git remote add origin https://github.com/michaelpeccia/tab-remote.git
git push -u origin main
```

> Push this from the PC, not Termux. The repo has a file under `.github/workflows/`,
> and your Termux token can't push those.
>
> No PC handy? On the new repo page, click **uploading an existing file** and drag
> in everything inside the TabRemote folder, including the `.github` folder.

## 1b. Or push it from Termux on the phone

Your usual Termux token can't push workflow files, so this uses the GitHub CLI (`gh`)
signed in with the `workflow` permission. It also creates the repo for you, so skip step 1.
First save `TabRemote.zip` to the phone's Download folder.

```bash
# one time: tools + sign in (choose "Login with a web browser", copy the code it shows)
pkg install -y git gh unzip
gh auth login -h github.com -p https -w -s workflow
gh auth setup-git

# unpack the project
cd ~ && rm -rf TabRemote
unzip -q /storage/emulated/0/Download/TabRemote.zip -d ~
cd ~/TabRemote

# commit, create the repo, push
git init -b main
git add .
git commit -m "Tab Remote v1"
gh repo create tab-remote --public --source=. --remote=origin --push

# watch the build (Ctrl+C to stop watching)
gh run watch --repo michaelpeccia/tab-remote

# when it's green, grab the APK into the phone's Download folder
gh release download --repo michaelpeccia/tab-remote --pattern TabRemote.apk --dir /storage/emulated/0/Download --clobber
```

If `git commit` asks who you are, run these once and commit again:
`git config --global user.name "Michael Ben Yah"` and `git config --global user.email "michaelbenyah@kingdom-life-ministry.com"`

Later updates from Termux: `cd ~/TabRemote && git add . && git commit -m "Update" && git push`

## 2. Get the APK

1. On the repo page, open the **Actions** tab. Wait for "Build APK" to show a green check (about 3 to 5 minutes).
2. Open the **Releases** section on the right side of the repo page, then download **TabRemote.apk**.
   Direct link: https://github.com/michaelpeccia/tab-remote/releases/latest

Every push builds a new release automatically.

## 3. Install on the tablet

1. On the tablet, go to **Settings → Security** and turn on **Unknown sources**.
2. Get the APK onto the tablet. Either:
   - plug the tablet into the PC by USB and copy `TabRemote.apk` into its **Download** folder, or
   - open the direct link above in the tablet's browser (this may fail on the old browser; USB always works).
3. Open **My Files → Download → TabRemote.apk → Install**.

## 4. Use it

- The tabs across the top switch between **Samsung TV**, **Vizio TV**, and **Blu-ray**.
- Hold down VOL, CH, the arrows, REW or FF to repeat.
- **1 2 3** opens the number pad.
- On the Roku page, POWER and volume only work on Roku TVs. Roku Streaming Sticks don't take IR at all.
- If the Roku doesn't respond, tap **CODE SET** on the Roku page to switch between the 4 Roku code sets, pressing HOME after each.
  The timing test on the Roku page sends HOME instead of POWER.
- On the Blu-ray page, the TV VOL buttons control the Samsung TV, like the real Blu-ray remote does.

## If a TV doesn't respond

1. **Check that the blaster fires.** Point the tablet at your phone's camera (the front camera works best)
   and press POWER. You should see a purple flash on the phone screen.
   - Flash but no reaction → go to step 2.
   - No flash → tap **SETUP**. If it says "IR blaster: NOT found", the current ROM is missing the IR driver.
2. **Run the timing test.** Tap **SETUP → TIMING TEST**. Tap **AUTO TEST** and watch the TV:
   it sends POWER eight different ways (A to H), three seconds apart, and shows which letter it's on.
   When the TV reacts, note the letter, then tap that letter. Whatever letter you tap last
   becomes the setting for every button. **RESET** goes back to the normal timing mode.
3. Get closer and aim straight at the TV. Tablet IR blasters are weaker than real remotes.

## Updating to a new version from Termux

Save the new `TabRemote.zip` to the phone's Download folder, then:

```bash
cd ~ && unzip -o -q /storage/emulated/0/Download/TabRemote.zip -d ~
cd ~/TabRemote
git add .
git commit -m "Update"
git push
gh run watch --repo michaelpeccia/tab-remote
gh release download --repo michaelpeccia/tab-remote --pattern TabRemote.apk --dir /storage/emulated/0/Download --clobber
```

Version 1.1 and later are signed with a fixed key, so they install over each other.
(Going from 1.0 to 1.1, uninstall 1.0 first.)

## This tablet's setting

Timing test letter **C** (microseconds, sent 3 times) is what works on this Tab 4. It stays saved,
but if you ever reinstall fresh, set it again in SETUP → TIMING TEST.

## Adding a device later (like the Android TV box)

The codes live in `app/src/main/java/com/kingdomlife/tabremote/Remotes.java`.
Tell Claude the brand and model and it can write the new block. Then:

```powershell
cd $HOME\Downloads\TabRemote
git add .
git commit -m "Add device"
git push
```

Wait for the new release, download the APK, and install it over the old one (your settings are kept).

## Code sources

- Samsung TV: IRDB `codes/Samsung/TV/7,7.csv` (Samsung32, device 7/7)
- Vizio TV: IRDB `codes/Vizio/Unknown_Vizio/4,-1.csv` (NEC, device 4)
- Roku: Flipper-IRDB Roku3 and TCL Roku TV captures (NECext EA C7; power = 57E3E817)
- Samsung Blu-ray: Flipper-IRDB captures of BD-D5300, BD-D7500, BD-E5300 and UBD-K8500. All four send identical codes (Samsung36)
