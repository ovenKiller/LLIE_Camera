package com.liushengqi.utils

import android.app.Activity
import java.lang.ref.WeakReference

object ActivityManager {
    // 弱引用
    private var sCurrentActivityWeakRef: WeakReference<Activity>? = null

    fun getCurrentActivity(): Activity? = sCurrentActivityWeakRef?.get()

    fun setCurrentActivity(activity: Activity) {
        sCurrentActivityWeakRef = WeakReference(activity)
    }
}