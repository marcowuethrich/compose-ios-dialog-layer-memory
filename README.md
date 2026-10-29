# Compose Multiplatform iOS: a closed Dialog keeps 34 MB of GPU memory

On iOS, with `parallelRendering = true` (the default), a `Dialog` or `Popup` is drawn by a
window-level layers view with its own Metal view and Skia `DirectContext`. When the last dialog
closes, Compose drains that view's drawables 500 ms after it leaves the window
([CMP-9918](https://youtrack.jetbrains.com/issue/CMP-9918)), but its Skia context keeps about 34 MB
of Metal textures on an iPhone 17 Pro-sized screen, for as long as the `ComposeUIViewController`
lives. This repository reproduces it with one dialog on an otherwise empty screen.

Proposed fix: [one commit](https://github.com/marcowuethrich/compose-multiplatform-core/commit/a7d7a6f78be7fc65b8c760f6316d08e987d23aff)
on the fork branch
[`fix/ios-layers-view-release-skia-context`](https://github.com/marcowuethrich/compose-multiplatform-core/tree/fix/ios-layers-view-release-skia-context),
off `jb-main` at `43692f76`: close the context with the drawables and create a new one for the next
frame. The same drain runs for a `ComposeUIViewController`'s own view once it has left the window,
so the fix releases that view's context too.

## Result

iPhone 17 Pro simulator (1206 x 2622 px), iOS 27.0, Release build. Live Metal textures at each mark
(width x height x bytes per pixel, 1 MB = 2^20 bytes):

| CMP | Rendering | Before the first dialog | Dialog open | 5 s and 20 s after closing it | Kept |
|---|---|---|---|---|---|
| 1.12.1 | parallel (default) | 40.2 MB | 98.4 MB | 74.3 MB | +34.1 MB |
| 1.12.2 | parallel (default) | 40.2 MB | 98.4 MB | 74.3 MB | +34.1 MB |
| 1.13.0-beta01 | parallel (default) | 16.1 MB | 74.3 MB | 50.1 MB | +34.0 MB |
| 1.13.10-alpha01+dev5084 | parallel (default) | 16.1 MB | 74.3 MB | 50.1 MB | +34.0 MB |
| `jb-main` at `43692f76` (local build) | parallel (default) | 16.1 MB | 74.3 MB | 50.1 MB | +34.0 MB |
| `jb-main` + the fix (local build) | parallel (default) | 16.1 MB | 74.3 MB | 16.1 MB | 0 |
| 1.13.0-beta01 | `parallelRendering = false` | 16.1 MB | 74.3 MB | 74.3 MB | +58.2 MB |

The second dialog ends at the same numbers as the first: the memory does not add up, but nothing
frees it while no dialog is shown. In parallel mode the 34 MB are three textures:

| Texture | Size | What keeps it |
|---|---|---|
| 1206 x 2622 BGRA8 | 12 MB | the layers view's last drawable, still referenced by Skia's last Metal command buffer until the context submits again (the next dialog) |
| 1536 x 3072 BGRA8 | 18 MB | the screen-sized offscreen layer of the dialog's fade animation (`alpha < 1`), still bound in the cached Metal pipeline state that drew it (`GrMtlPipelineState`'s sampler bindings) until that pipeline draws again (the next dialog) |
| 2048 x 2048 R8 | 4 MB | the context's glyph atlas |

With `parallelRendering = false` nothing is drained at all (that path goes away with
[CMP-9722](https://youtrack.jetbrains.com/issue/CMP-9722)).

## Run it

Needs a Mac with Xcode and an iOS simulator runtime, and a JDK 17 or newer.

```bash
./gradlew linkReleaseExecutableIosSimulatorArm64 && ./run.sh
```

`run.sh` wraps the Kotlin/Native executable in an `.app`, installs it on a new iPhone 17 Pro
simulator, runs it once with the default rendering and once with `parallelRendering = false`,
prints its `REPRO` lines and deletes the simulator. Another CMP version:
`./gradlew -PcomposeVersion=1.12.1 linkReleaseExecutableIosSimulatorArm64`. Another simulator:
`DEVICE_TYPE=com.apple.CoreSimulator.SimDeviceType.iPhone-16 RUNTIME=com.apple.CoreSimulator.SimRuntime.iOS-27-0 ./run.sh`
(both optional).

The app ([`Main.kt`](src/iosMain/kotlin/Main.kt)) shows a white screen, waits 5 s, then twice opens a
`Dialog` for 3 s and closes it, and waits 5 s; then it waits 15 s more and exits. At each mark it
logs the Metal textures that are still alive.
[`metaltextures.def`](src/nativeInterop/cinterop/metaltextures.def) wraps the Metal device's two
texture factory methods and keeps every texture in a weak table, so it never keeps one alive
itself. (`MTLDevice.currentAllocatedSize` reports 0 on the simulator.)

**Expected:** once the dialog is closed and the layers view is drained, the live textures are back
at the value before the first dialog.
**Actual:** 34 MB more, until the `ComposeUIViewController` is disposed.

## Environment

| | |
|---|---|
| Simulator | iPhone 17 Pro, iOS 27.0 (24A434) |
| Mac | Apple M5 Pro, macOS 27.2, Xcode 27.1 (27A9275) |
| Compose Multiplatform | 1.12.1, 1.12.2, 1.13.0-beta01, 1.13.10-alpha01+dev5084, `jb-main` at `43692f76` (local build) |
| Kotlin | 2.4.20 |
| Gradle | 9.6.1 |

## License

MIT, see [LICENSE](LICENSE).
