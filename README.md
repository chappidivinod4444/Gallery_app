# Gallery

A clean, modern, private gallery for Android with a full photo editor and an encrypted Vault.

**Private by design:** the app has no internet permission, so photos can never leave the phone.

## Features
- **Photos** timeline grouped by day (or month when zoomed out); pinch to change the grid size
- Long-press and drag to select many; share, favourite, copy/move to album, hide, delete (to Trash)
- **Albums**, Videos, Screenshots, Favourites, **Trash** (restore / delete forever / empty)
- **Search** by name, album, month, weekday or year, plus quick filters (videos, GIFs, large files…)
- **Viewer**: swipe, pinch / double-tap zoom, video playback, details (camera, exposure, location),
  slideshow, set as wallpaper, open with, rename
- **Editor** (saves a copy, the original is never changed):
  crop with aspect ratios, rotate, flip, straighten · 23 filters with strength ·
  Auto plus 14 adjustments (exposure, brightness, contrast, highlights, shadows, saturation, vibrance,
  warmth, tint, fade, vignette, sharpen, blur, grain) · draw (pen, marker, neon, eraser) ·
  text (5 fonts, colours, box) · emoji stickers · frames and watermark ·
  undo / redo · hold to compare with the original
- **App lock** with fingerprint, face or screen lock; locks again when you leave
- **Vault**: hidden photos and videos encrypted with AES-256-GCM (64 KB authenticated chunks); the key is
  wrapped by a hardware-backed Android Keystore key. Always asks to unlock, blocks screenshots,
  closes when you leave the app, never writes decrypted copies to shared storage.
- Material You colours, light / dark / pure black themes

## Build
GitHub Actions builds and publishes `Gallery.apk` as a release on every push to `main`.
