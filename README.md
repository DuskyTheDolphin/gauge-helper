# Gauge Helper

A client-side NeoForge mod for Minecraft 1.21.1 that adds a small helper panel beside Create's **Factory Gauge
screen**: how many to send for a recipe with a chance output, a suggested input amount, and **saved gauge presets you
fill into another gauge with a two-click Apply and an Undo**.

It adds no blocks, gauges, items or network packets. The panel only reads Create's own screen, and a preset only fills
in that screen's own fields; Create then sends them exactly as if you had typed them. What a gauge does is unchanged.

Designed by DuskyTheDolphin, written with AI assistance.

## What it does

Every helper is **off by default**; turn on the ones you want (see Config).

- **Chance helper** (`chanceHelper`): for a Create recipe with a chance output (crushing, splashing, sequenced assembly
  and the like), how many to send on average to get 1, and any byproducts that come out too and can clog a line.
- **Suggested amount** (`suggestedAmount`): the expected yield per send and a suggested input count for the gauge's
  target. Display only: nothing is filled in for you. A note shows when a suggestion passes the gauge's 64-per-request
  input limit.
- **Presets** (`presets`): **Save** a gauge's address, output amount, input amounts and promise expiry, then fill them
  into another gauge's screen. Presets for the open gauge's item come first.
  - **Apply takes two clicks**: the first says "Click again to overwrite.", the second fills the fields. Create sends a
    gauge screen's fields whenever the screen closes, Esc included, so a mistaken fill can't be cancelled by leaving.
  - **Undo** puts back the fields from before the latest Apply, as many times as you applied, while the screen is open.
  - **Del** also asks "Sure?" first.
  - A preset does not carry the gauge's filter item or target count (the screen's own packet doesn't send them), and the
    panel says so.

When several recipes make the gauge's item, the panel lists them and you pick one. With inputs wired to the gauge, only
recipes those inputs fit are counted; a partial match says nothing rather than something wrong.

## Using it

Open a Factory Gauge's screen. The panel sits to the left of Create's window, out of the way of its slot tooltips and
icon. Its helper text scrolls with the mouse wheel; the recipe picker stays at the top and the preset buttons at the
bottom, so nothing moves under the mouse while you click. If the window leaves no room beside it, the panel is not
drawn over Create's window: a one-line hint, "Gauge Helper: lower your GUI scale", appears below it instead.

On a gauge in crafting mode (it sends its own arrangement) the panel shows nothing, and the chance and amount lines are
not shown on a restocking gauge.

Presets are stored on your computer only, in `config/forgeward_gaugehelper_presets.json` (up to 64). If that file
can't be read, the list starts empty and the file is copied to `.bad` before the next save, so it is never lost.

## Install

Client only. Put the jar in the client's `mods/` folder; servers do not need it, and it does nothing on one.

- Minecraft 1.21.1
- NeoForge 21.1 or newer (built and tested against 21.1.250)
- Create 6.0 or newer, required (built against Create 6.0.10)

Release test for 1.0.7: a NeoForge 21.1.250 client with only Create 6.0.10 and this mod installed, joined to a server
with Create; it reported ready and indexed the loaded recipes.

## Config

`config/forgeward_gaugehelper-client.toml`, also editable in game from the Mods list (Config).

| Key | Default | Meaning |
|---|---|---|
| `chanceHelper` | `false` | For a recipe with a chance output: how many to send, on average, for 1, plus any byproducts |
| `suggestedAmount` | `false` | The expected yield per send and a suggested input count. Display only |
| `presets` | `false` | Save a gauge's address, output and input amounts, and fill them into another gauge's screen |

## Log

```
[gaugehelper] 1.0.7 ready: chanceHelper=off suggestedAmount=off presets=off (0 saved)
[gaugehelper] index: <N> processing/sequenced recipes, <M> with a chance output, 0 skipped
[gaugehelper] gauge screen opened: all toggles off, nothing drawn
```

The first line comes at start-up and on every config change. The index line comes each time the recipes reach the
client (joining a world or server, `/reload`; its counts depend on the recipes loaded), followed by two sample lines
that print real chance recipes through the same code the panel uses. Saving, applying, undoing and deleting a preset each log one line.

## Building

`build.ps1` compiles with `javac` against a NeoForge 21.1.250 server's `libraries/` (set `SERVER` and `JDK`) and a
Create jar (`CREATE`, or the newest `create-1.21.1-*.jar` in the server's `mods/`). The named client jars go in
`clientlibs/`; when they are missing, the script makes them with the server's NeoForge installer in client mode.
Mod id `forgeward_gaugehelper`.

## License

MIT, see [LICENSE](LICENSE).
