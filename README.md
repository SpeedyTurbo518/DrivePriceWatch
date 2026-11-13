# Drive Price Watch

Android app that tracks prices on a list of 6TB hard-drive listings (eBay, ServerPartDeals, Newegg, Best Buy) and notifies you when a price changes, a listing ends, or a price crosses $200.

**Download:** grab `DrivePriceWatch.apk` from the [Releases](../../releases) page.

## Build the APK yourself

You only need to do this once. Android Studio downloads what it needs the first time (requires internet).

1. Clone or download this repository.
2. Android Studio → **File → Open** → pick this folder (the one with `settings.gradle`).
3. Wait for **Gradle sync** to finish (bottom status bar). If Android Studio offers to upgrade the Android Gradle Plugin or Gradle, it's fine to accept.
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   When it finishes, click **locate**. The file is `app/build/outputs/apk/debug/app-debug.apk`.
5. Install on your phone, either:
   - Plug in the phone by USB (Developer options + USB debugging on) and press **Run ▶**, or
   - Copy `app-debug.apk` to the phone, tap it in My Files, and allow "Install unknown apps" for My Files when asked.
6. Open **Drive Price Watch** and tap **Allow** for notifications.
7. Important on Samsung: **Settings → Apps → Drive Price Watch → Battery → Unrestricted**.
   Without this, Samsung's battery saver can stop the daily 12:45 PM check.

## How it works
- Works fully offline with the last saved prices. Your data stays on the phone.
- Every day at ~12:45 PM (through Nov 25, 2026) it checks all 14 listings once the phone has internet.
  It notifies you when any price goes up or down, when a listing ends, or when a price crosses $200.
- **Check prices now** in the app does the same immediately. If a store blocks the quick check, the app
  retries that page in a hidden browser, which gets through more often than the background check.
- Best Buy and Newegg sometimes block background checks. If they do, open the app and tap **Check prices now**.
