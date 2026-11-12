# Tab Remote: setup notes

An offline IR remote app for the Galaxy Tab 4 7.0 (SM-T230NU).
Controls: Samsung TV, Vizio TV, Samsung Blu-ray. All codes are built in, so it never needs internet.

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
- On the Blu-ray page, the TV VOL buttons control the Samsung TV, like the real Blu-ray remote does.

## If a TV doesn't respond

1. **Check that the blaster fires.** Point the tablet at your phone's camera (the front camera works best)
   and press POWER. You should see a purple flash on the phone screen.
   - Flash but no reaction → go to step 2.
   - No flash → tap **SETUP**. If it says "IR blaster: NOT found", the current ROM is missing the IR driver.
2. **Change the timing mode.** Tap **SETUP → Change timing**, then **Test POWER**. Try all three modes
   (Auto, Manual microseconds, Manual carrier cycles). Older KitKat builds (4.4.2 and earlier)
   need carrier cycles, while CyanogenMod / Android 5+ needs microseconds. Auto picks one based on the Android version.
3. Get closer and aim straight at the TV. Tablet IR blasters are weaker than real remotes.

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
- Samsung Blu-ray: Flipper-IRDB captures of BD-D5300, BD-D7500, BD-E5300 and UBD-K8500. All four send identical codes (Samsung36)
