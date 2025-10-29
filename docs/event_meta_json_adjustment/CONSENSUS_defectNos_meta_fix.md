# CONSENSUS_defectNos_meta_fix

## Background

- Symptom: Local Room `event.defect_nos` contains values, but packaged `meta.json` shows `defectNos` as empty array `[]` during cloud sync.
- Impact: Server cannot correlate event with defects by number, breaking downstream linking and dashboards.

## Root Cause

1. `meta.json` is generated at creation time using in-memory `selectedDefects.map { it.defectNo }`. If defects are updated later (e.g., re-link or DB migration), the directory’s `meta.json` is not refreshed.
2. Packaging step (`createEventZip`) previously only fixed legacy audio extensions and `structuralDefectDetails`, not reconciling `defectNos` with the current DB row.

## Requirements & Acceptance Criteria

- Before zipping, ensure `meta.json.defectNos` matches the latest `event.defect_nos` in Room.
- Only patch `meta.json` when the field is missing or empty; do not override non-empty values written recently by UI.
- Backward-compatible and idempotent; no impact to other fields or flows.

## Solution

- Repository-layer reconciliation: extend the pre-zip fix to populate `defectNos` from DB when `meta.json` is missing or empty.
- Implementation:
  - In `EventRepository.createEventZip(...)`, fetch `eventEntity` by `uid` and pass `eventEntity.defectNos` into `fixLegacyPlaceholderExtensions(...)`.
  - In `fixLegacyPlaceholderExtensions(...)`, if `meta.json.defectNos` is `null` or `[]` and DB list is not empty, write `defectNos` as a JSON array.

## Boundaries

- No changes to Room schema, Retrofit interfaces, or UI behaviors.
- Do not modify `defectIds` logic; only reconcile `defectNos` when absent.

## Integration & Constraints

- MVVM + Room + Hilt architecture preserved.
- Fix executes on local packaging only; safe if event directories are legacy.

## Verification Steps

1. Create or edit an event so that `event.defect_nos` has values.
2. Trigger sync; after pre-zip fix, open `files/events/<uid>/meta.json` and confirm `defectNos` contains those values.
3. For legacy directories with empty `defectNos`, verify the packaged zip includes updated `meta.json`.

## Quality Gates

- Clear scope and acceptance criteria defined.
- Aligned with existing architecture; no side effects.
- Testable with local packaging validation.
- Assumptions documented: DB has the source of truth when meta is empty.