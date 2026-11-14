package com.lightninglab

/**
 * Pure recovery-selection rules used by the Android draft store.
 * Primary is preferred only when its structure and referenced overlays are intact.
 * A previous snapshot is independent and can rescue a corrupt/incomplete primary.
 */
internal object VideoDraftRecoveryPolicy {
    fun references(draft: VideoDraft?): Set<String> =
        draft?.project?.overlayClips?.mapNotNull { it.assetFile }?.toSet().orEmpty()

    fun usable(draft: VideoDraft?, assetExists: (String) -> Boolean): Boolean =
        draft != null &&
            VideoDraftRestoreRules.validate(draft) &&
            draft.project.overlayClips.all { clip ->
                val name = clip.assetFile
                name == null || (VideoDraftAssets.isSafe(clip.id, name) && assetExists(name))
            }

    fun select(
        current: VideoDraft?,
        previous: VideoDraft?,
        assetExists: (String) -> Boolean,
    ): VideoDraft? = when {
        usable(current, assetExists) -> current
        usable(previous, assetExists) -> previous
        else -> null
    }

    fun protectedAssets(current: VideoDraft?, previous: VideoDraft?): Set<String> =
        references(current) + references(previous)
}
