package com.chagok.chagok.ui.common

import android.app.Activity
import android.content.Intent
import com.chagok.chagok.MainActivity
import com.chagok.chagok.ui.consent.ConsentActivity
import com.chagok.chagok.ui.permission.PermissionActivity
import com.chagok.chagok.ui.state.Screen
import com.chagok.chagok.ui.profile.ProfileActivity

/** 화면 → Activity. 새 화면이 생기면 여기에만 추가하면 된다 */
fun Activity.intentFor(screen: Screen): Intent = when (screen) {
    Screen.PERMISSION -> Intent(this, PermissionActivity::class.java)
    Screen.CONSENT -> Intent(this, ConsentActivity::class.java)
    Screen.PROFILE -> Intent(this, ProfileActivity::class.java)
    // TODO: S-05·S-06·S-07 Activity가 생기면 추가
    else -> Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, screen.name)
}