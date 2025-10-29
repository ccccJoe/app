# CONSENSUS: Event meta.json asset filenames fix

## Clear Requirements and Acceptance Criteria
- Change `meta.json` fields `photoFiles` and `audioFiles` to contain the filenames that exist inside the event ZIP, not absolute device paths.
- Audio filenames must use the `.m4a` extension to match the packaged assets.
- When saving or uploading an event, the generated `meta.json` under `files/events/<uid>/meta.json` must list items like `photo_0.jpg`, `photo_1.jpg`, `audio_0.m4a`, etc.
- Building the app succeeds; no schema or runtime exceptions introduced by the change.

## Technical Solution and Constraints
- App architecture: MVVM + Jetpack, Kotlin (JDK17), Room, Retrofit/OkHttp, Hilt.
- Implementation points:
  - In `EventFormViewModel.saveEventToRoom`: write `photoFiles` as `photo_<index>.jpg` and `audioFiles` as `audio_<index>.m4a` when constructing `metaData` before copying assets.
  - In `EventFormViewModel.saveEventToLocal`: copy audio files as `audio_<index>.m4a` and persist the same filenames in `meta.json`.
- No UI changes; no DTO or API schema changes.
- Room entities unaffected; `EventEntity.riskAnswers` remains `String?` and `riskScore` remains `Double?`.

## Task Boundaries
- Only adjust how `meta.json` lists asset filenames. Do not alter zip creation, hashing, or upload APIs.
- Do not migrate historical `meta.json` files; legacy fix is out-of-scope.

## Integration Details
- `EventFormViewModel.kt` writes `meta.json` and copies assets into `events/<uid>/` directory. The ZIP is later created from this directory, so listing filenames in `meta.json` aligns with packaged content.
- `EventRepository.createEventZip` zips the entire event directory; no change needed.

## Uncertainties Resolved
- Filenames inside ZIP are deterministic: `photo_<index>.jpg` and `audio_<index>.m4a`.
- Audio extension is standardized to `.m4a` across save paths.

## Verification Steps
- Create an event with 2 photos and 1 audio. Inspect `files/events/<uid>/meta.json` to confirm:
  - `photoFiles`: `["photo_0.jpg", "photo_1.jpg"]`
  - `audioFiles`: `["audio_0.m4a"]`
- Confirm the event directory contains files with the same names.
- Build succeeds: `./gradlew :app:assembleDebug`.

## Quality Gates
- Requirements precise and testable.
- Solution aligned with current architecture; no side effects.
- Acceptance criteria verifiable via local file inspection.
- Key assumptions documented and confirmed.
- Uses native Android file I/O; no external dependencies added.