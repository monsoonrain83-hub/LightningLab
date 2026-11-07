# Why the r12 timeline editor is parked (build 1.0-r10.2)
An update zip overwrote shared r12 files (MainVM.kt, Screens.kt, strings.xml, build.gradle.kts, ...), leaving r12's
VideoEditorScreen.kt/Timeline.kt/TimedOverlays.kt without their ViewModel members (26), strings (~63) and the
media3-exoplayer dependency, and with a second `VideoEditor` composable. The three files are stubbed here and their
original source is in docs/parked_r12_editor/*.kt.txt. Full r12 sources are in the git history of the repository.
