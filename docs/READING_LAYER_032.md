# Projector Reading Layer 0.3.2

This describes implementation, not completed native acceptance.

## Coordinate Ownership

At native GUI scale 1 and a screen of at least 640x480, the dedicated reading
screen uses a private logical canvas with half the logical width and height,
rounded up. Text, callouts, paging and GUI snapshots share this canvas. Other
GUI scales and smaller screens keep their existing dimensions. The global
Minecraft GUI scale, projector configuration screen and ordinary projector
HUD are not changed.

The world retains its captured model-view, projection and viewport. A copied
projection maps target positions to the reader canvas; it does not move or
reframe the world. Mouse coordinates and framebuffer scissors use the exact
screen-to-canvas ratios, including odd dimensions. The overlay's orthographic
pass is established at the reader dimensions rather than applying a matrix
scale around a renderer that would reset it.

Temporary renderer dimensions and the clipping context are restored in a
`finally` block. Closing or resizing the reading screen invalidates old hit
areas. The reader remains non-pausing and consumes its captured input; it
does not call real player interaction or change a projector's configuration.

## Evidence Boundary

Unit coverage includes coordinate conversion, boundaries, odd framebuffer
dimensions, unchanged projection snapshots and nested/throwing callback
restoration. Runtime checks still need the exact 0.3.2 JAR and QA source
identity at 1280x720, 1920x1080, other GUI scales and compact Unicode layouts.
Programmatic screen callbacks do not establish physical input or audible
sound. Do not transfer old 0.3.1 screenshots or test results to this candidate.

All development and private test artifacts stay under `C:\CODEX`. The running
user instance remains untouched while development and offline checks proceed.
