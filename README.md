# MIRACLO: frameworks/base

Android 17 `frameworks/base` from Qualcomm CLO with MIRACLO's changes. Branch: `aurora`.
Private: access on request (see [MIRACLO](https://github.com/MIRACLO-Project/MIRACLO)).

## Where MIRACLO's work lives

| Area | Path |
|---|---|
| Quick Settings styles (ColorOS, iOS, One UI), edit mode, sizes, widgets | `packages/SystemUI/src/com/android/systemui/qs/composefragment/MiraProto*.kt`, `MiraCLO*.kt` |
| Quick Settings overlay (pull-down, landscape) | `packages/SystemUI/compose/features/src/com/android/systemui/qs/ui/composable/QuickSettingsShadeOverlay.kt` |
| Real tiles drawn in the styles' looks | `packages/SystemUI/src/com/android/systemui/qs/panels/ui/compose/infinitegrid/` (`MiraTileSkin`, `InfiniteGridLayout`) |
| Island, Liquid Glass, power menu Advanced, sleep, status bar height | `packages/SystemUI/src/com/android/systemui/miraclo/` |
| Battery shape per icon style | `packages/SystemUI/src/com/android/systemui/statusbar/pipeline/battery/` |
| Wi-Fi icons (Android 17 everywhere) | `packages/SystemUI/src/com/android/systemui/statusbar/pipeline/wifi/ui/model/WifiIcon.kt` |
| Motion styles (Standard, Expressive, Snappy) | `packages/SystemUI/compose/core/src/com/android/compose/theme/MiraMotion.kt`, SettingsLib Spa `MiraMotion.kt` |
| Wavy progress bars and spinners | `core/java/com/android/internal/widget/MiraWavyProgressDrawable.java`, `core/java/android/widget/ProgressBar.java` |
| Round Settings icons | `packages/SettingsLib/AdaptiveIcon/.../AdaptiveIconShapeDrawable.java` |
| Stand-ins for the stock camera's MIUI classes | `core/java/miui/` |
| System UI permissions (power menu reboots) | `data/etc/com.android.systemui.xml` |

Settings keys MIRACLO reads are all `Settings.Secure` `miraclo_*`.

## Rules for changes
- Commits by PSP only; no other names, no co-author lines.
- A new permission in System UI's manifest must also go into `data/etc/com.android.systemui.xml`,
  or the phone will not boot.
- New `miui.*` (or other non-Android) packages in the framework must be listed in
  `build/soong/scripts/check_boot_jars/package_allowed_list.txt` (see the manifest's patches).
