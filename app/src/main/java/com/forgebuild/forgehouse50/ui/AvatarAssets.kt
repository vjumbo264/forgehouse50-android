package com.forgebuild.forgehouse50.ui

import com.forgebuild.forgehouse50.R

/**
 * combined_fixes_v1 / Issue 2: the full 41-illustration avatar set (the same
 * assets committed to the web repo's public/avatars/) is BUNDLED in the app
 * under res/drawable-nodpi — avatar selection renders instantly with zero
 * network dependency. Only the avatar_id is ever sent to the backend,
 * exactly as the web app does; bundling is purely on-device rendering.
 */
object AvatarAssets {
    val IDS: List<String> = (1..41).map { "avatar-%02d".format(it) }

    fun resFor(id: String?): Int = when (id) {
        "avatar-01" -> R.drawable.avatar_01
        "avatar-02" -> R.drawable.avatar_02
        "avatar-03" -> R.drawable.avatar_03
        "avatar-04" -> R.drawable.avatar_04
        "avatar-05" -> R.drawable.avatar_05
        "avatar-06" -> R.drawable.avatar_06
        "avatar-07" -> R.drawable.avatar_07
        "avatar-08" -> R.drawable.avatar_08
        "avatar-09" -> R.drawable.avatar_09
        "avatar-10" -> R.drawable.avatar_10
        "avatar-11" -> R.drawable.avatar_11
        "avatar-12" -> R.drawable.avatar_12
        "avatar-13" -> R.drawable.avatar_13
        "avatar-14" -> R.drawable.avatar_14
        "avatar-15" -> R.drawable.avatar_15
        "avatar-16" -> R.drawable.avatar_16
        "avatar-17" -> R.drawable.avatar_17
        "avatar-18" -> R.drawable.avatar_18
        "avatar-19" -> R.drawable.avatar_19
        "avatar-20" -> R.drawable.avatar_20
        "avatar-21" -> R.drawable.avatar_21
        "avatar-22" -> R.drawable.avatar_22
        "avatar-23" -> R.drawable.avatar_23
        "avatar-24" -> R.drawable.avatar_24
        "avatar-25" -> R.drawable.avatar_25
        "avatar-26" -> R.drawable.avatar_26
        "avatar-27" -> R.drawable.avatar_27
        "avatar-28" -> R.drawable.avatar_28
        "avatar-29" -> R.drawable.avatar_29
        "avatar-30" -> R.drawable.avatar_30
        "avatar-31" -> R.drawable.avatar_31
        "avatar-32" -> R.drawable.avatar_32
        "avatar-33" -> R.drawable.avatar_33
        "avatar-34" -> R.drawable.avatar_34
        "avatar-35" -> R.drawable.avatar_35
        "avatar-36" -> R.drawable.avatar_36
        "avatar-37" -> R.drawable.avatar_37
        "avatar-38" -> R.drawable.avatar_38
        "avatar-39" -> R.drawable.avatar_39
        "avatar-40" -> R.drawable.avatar_40
        "avatar-41" -> R.drawable.avatar_41
        else -> R.drawable.avatar_01
    }
}
