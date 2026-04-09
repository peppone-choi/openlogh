# LOGH Voxel Ship Pipeline Design

Date: 2026-04-09
Status: Draft approved in conversation, written for review

## Goal

Build a ship asset pipeline for Open LOGH that can:

- produce true 3D voxel ships as the final target
- derive 2D isometric or quarter-view sprites from the same source
- preserve original `gin7` ship data as accurately as possible
- automate extraction as much as possible, while surfacing uncertainty instead of silently accepting bad outputs

The target quality bar is as close to original game data as practical. Existing extracted models may be reused, but they are not authoritative.

## Requirements

- Final target is `1`: rotatable 3D voxel ships in-game.
- `2` and `3` are also required:
  - 2D voxel-derived sprites
  - reusable voxel source assets
- Accuracy target is `C`:
  - named ships should match original ships
  - proportions, silhouette, and distinguishing details should be preserved unless the original source data is incomplete or contradictory
- Workflow must be mostly automatic.
- Existing extracted models are known to contain errors, so they cannot be the source of truth.

## Source Hierarchy

Ship data should be resolved from the following sources in priority order:

1. Original game data from `LOGHPC.zip`
2. Official reference material such as `gin7manual.pdf`
3. Existing extracted assets from `openlogh-image`

Interpretation:

- `LOGHPC.zip` is the authoritative asset source.
- `gin7manual.pdf` is primarily for naming, classification, and verification.
- `openlogh-image` is a useful secondary dataset for comparison, fallback, and bootstrap, but not the canonical truth.

## Why This Direction

Three broad approaches were considered:

1. Voxelize existing extracted OBJ data directly
2. Re-extract from original game data and rebuild a clean pipeline
3. Re-extract from original data, but use existing OBJ output as a secondary reference

Approach `3` is recommended.

Reasoning:

- `1` is fastest, but locks existing extraction mistakes into the final voxel assets.
- `2` is the cleanest in principle, but throws away useful prior work.
- `3` preserves the correctness bar of `2` while still taking advantage of the work already done in `openlogh-image`.

## High-Level Pipeline

The pipeline should have these stages:

1. `extract`
2. `identify`
3. `normalize`
4. `voxel-source`
5. `derive-3d`
6. `derive-2d`
7. `verify`

### 1. extract

- unpack `LOGHPC.zip`
- locate game data under `gameroot/data`
- extract `.mvx` and `.arc` containers
- preserve raw dumps and extraction metadata

`LoGHTools` already demonstrates the core container parsing model for `.mvx` and `.arc`, including TOC parsing and LZSS decompression. That should be reused or ported rather than reverse-engineered from scratch.

### 2. identify

- classify extracted files by role:
  - names and labels
  - unit definitions
  - textures
  - model or scene candidates
- connect resources that appear to belong to the same ship
- build a ship candidate index with confidence scores

The hardest part is not geometry extraction by itself, but identifying which asset corresponds to which named ship.

### 3. normalize

Convert identified ship assets into a common representation:

- consistent forward axis
- consistent origin and pivot
- consistent scale rules
- stable ship bounding volume
- optional semantic part grouping

Normalization is required before voxelization. Without it, all downstream outputs drift in scale, orientation, and silhouette.

### 4. voxel-source

Create one canonical voxel representation per ship.

This is the master source of truth for all downstream outputs.

### 5. derive-3d

Generate rotatable 3D voxel assets from the voxel source.

This is the final target output for in-game use.

### 6. derive-2d

Render 2D sprites from the exact same voxel source.

This ensures that 2D icons, portraits, tactical sprites, and previews stay consistent with the 3D voxel assets.

### 7. verify

Run structural, semantic, and visual verification:

- original asset linkage is intact
- ship naming and faction classification are correct
- silhouette and proportions remain consistent with the normalized canonical source and ship-specific verification references
- outputs are reproducible from canonical voxel source data

## Canonical Voxel Asset Format

The canonical ship format should be real voxel data, not a regular mesh dressed up with a voxel look.

Recommended per-ship structure:

- `ship.json`
  - ship id
  - display name
  - faction
  - class
  - subtype or named-ship identifier
  - source provenance
  - confidence values
  - scale and axis rules
- `voxels.bin` or chunked voxel payload
  - occupancy data
  - material or color indices
- `palette.json`
  - hull colors
  - windows
  - engine glow
  - accent materials
- `parts.json`
  - hull
  - bridge
  - engines
  - turrets
  - wings or protrusions
- `sources.json`
  - source files
  - extraction log
  - fallback usage
  - comparison references

Why real voxel data is preferred:

- 3D voxel output becomes straightforward
- 2D sprite generation becomes deterministic
- future damage, destruction, cutting, and part replacement stay possible
- re-running the pipeline regenerates all derivatives from a single source

## Automated Extraction Strategy

The pipeline should separate extraction from interpretation.

### Structural extraction

- dump all accessible resources from original containers
- build an index of filenames, offsets, sizes, hashes, and relationships
- avoid early assumptions about file type

### Semantic identification

- infer candidate model, texture, and metadata files
- link ship definitions to labels and to visual resources
- assign confidence instead of forcing certainty

### Candidate scoring

Each ship candidate should be scored from multiple signals:

- original internal naming or references
- unit table linkage
- texture pairing
- shape consistency
- similarity to existing `openlogh-image` outputs
- manual reference agreement

Each final classification should end in one of:

- `confirmed`
- `high-confidence`
- `suspect`
- `conflict`
- `unknown-candidate`

This allows automatic processing without pretending every result is equally trustworthy.

## Existing Assets Usage Policy

Existing assets from `openlogh-image` should be used in these roles:

- bootstrap reference for ship inventory
- comparison target for validation
- fallback source when the original extraction path is temporarily incomplete

They should not override original game data when the two disagree.

## Error Handling

The pipeline should fail loudly for correctness issues, but remain resumable.

### Container extraction failure

- record failed file
- record hash and offsets
- continue processing unrelated files
- allow targeted parser fixes and reruns

### Identification failure

- retain file as `unknown-candidate`
- do not discard possibly relevant data

### Naming conflict

- score all candidate matches
- keep non-winning candidates attached as unresolved evidence
- never flatten conflicts into a false certainty

### Voxelization failure

- mark as failed
- do not emit a misleading empty or malformed final asset

### Derivative generation failure

- keep canonical voxel source
- allow isolated reruns of `derive-3d` or `derive-2d`

## Verification Model

Verification should run on three layers.

### Structural verification

- archive extraction count is sane
- expected references resolve
- file linkage remains intact

### Semantic verification

- ship name matches
- faction matches
- ship class matches
- named ship identity is not silently degraded into a generic ship

### Shape verification

- facing direction is correct
- overall silhouette is stable
- major proportions remain consistent
- key identifying forms are preserved

## Testing Strategy

Use a small, named subset of ships as the regression backbone.

Recommended test categories:

- extraction regression tests for representative `.mvx` and `.arc` assets
- golden pipeline tests for `extract -> identify -> normalize -> voxelize -> derive`
- named-ship regression tests for important unique ships
- visual comparison tests against known-good extracted assets
- image snapshot tests for 2D sprite outputs

The initial regression set should include both:

- generic class ships
- uniquely identifiable named flagships

## Non-Goals

This design does not attempt to:

- solve all parser reverse engineering in one pass
- require perfect certainty on every asset before any output is produced
- treat existing OBJ assets as canonical
- use full freeform 3D generation as the primary asset source

## Proposed Repository Shape

Suggested directory layout:

```text
tools/logh-extract/         original container extraction
tools/logh-identify/        ship candidate mapping and confidence scoring
tools/logh-normalize/       axis/scale/origin normalization
tools/logh-voxelize/        canonical voxel generation
tools/logh-derive-3d/       3D voxel mesh export
tools/logh-derive-2d/       2D sprite rendering
assets/ships/source/        canonical voxel source data
assets/ships/derived-3d/    exported 3D voxel assets
assets/ships/derived-2d/    rendered sprite outputs
reports/ship-verification/  machine-readable verification reports
```

Exact placement can be adapted to the current repo structure, but the separation of concerns should remain.

## Decision Summary

- Final target remains true 3D voxel ships.
- 2D sprites and reusable source assets must derive from the same canonical voxel source.
- Original game data is the source of truth.
- Existing extracted OBJ assets are secondary evidence only.
- The recommended strategy is original-data-first with existing assets used as comparison and fallback support.
- Canonical assets should be stored as real voxel data plus provenance and verification metadata.
