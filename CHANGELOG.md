# Changelog

## 1.0.7
- First public release. No behaviour change.
- Display name "Gauge Helper"; MIT license.
- The build script no longer assumes a fixed folder layout (`SERVER`, `JDK`, `CREATE`).

## 1.0.6
- Unreleased internal version: Apply takes two clicks ("Click again to overwrite."), and Undo restores the fields from
  before it while the screen is open. Create sends a gauge screen's fields on every close, Esc included, so a mistaken
  Apply could not be cancelled.

## 1.0.5
- Unreleased internal version: the panel's buttons hold still. The text has a fixed box and the preset name and status
  hold two lines each, so a new status line can't move Save onto Apply. Re-saving an existing preset logs.

## 1.0.4
- Unreleased internal version: never draws over Create's window. With no room beside it, only a one-line hint shows
  below the window.

## 1.0.3
- Unreleased internal version: fits at Auto GUI scale on 1080p. The recipe picker and presets stay pinned, the helper
  text between them scrolls with the mouse wheel, and buttons take two rows on a narrow panel.

## 1.0.2
- Unreleased internal version: the panel moves left of the window, clear of Create's gauge icon and slot tooltips, and
  draws under tooltips. Presets are hidden on a gauge in crafting mode.

## 1.0.1
- Unreleased internal version: the panel says what a preset leaves out (filter and target count).

## 1.0.0
- Unreleased internal version: chance helper, suggested amount and presets, each off by default in a client config.
