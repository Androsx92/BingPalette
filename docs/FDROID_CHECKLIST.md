# F-Droid submission checklist

1. Make the GitHub repo public (Settings > General > Danger zone > Change visibility).
2. Keep `LICENSE` (GPL-3.0) at the top level. Add your name to the README.
3. Upload the whole project including `gradlew`, `gradle/wrapper/`, `fastlane/`, `LICENSE`, `.gitignore`, `docs/`.
4. Add 2-4 screenshots to `fastlane/metadata/android/en-US/images/phoneScreenshots/` (1.png, 2.png ...).
   Use screenshots that do NOT show a copyrighted Bing image in full, or ones you are comfortable with.
5. Create a release tag: GitHub > Releases > Draft a new release > tag `v2.2` (must match versionName).
6. Make sure the Actions build is green for that tag.
7. Fork https://gitlab.com/fdroid/fdroiddata, add `metadata/app.bingpalette.yml` from `docs/fdroid-recipe-draft.yml`
   (replace YOURNAME), and open a merge request. Reviewers may ask for changes; answer them there.
8. Read https://f-droid.org/docs/Inclusion_Policy/ and https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/ first.
9. After approval, F-Droid builds and signs the app with its own key. Your sideloaded GitHub build uses a different key,
   so uninstall it once before installing the F-Droid version.

Possible review questions: why an accessibility service (see store text), the name "Bing" (trademark), NonFreeNet anti-feature.
