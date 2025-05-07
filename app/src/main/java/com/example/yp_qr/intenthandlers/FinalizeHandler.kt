package com.example.yappy.intenthandlers

import android.app.Activity
import android.content.Intent

class FinalizeHandler(private val activity: Activity) {

    fun handle() {
        val result = Intent("icg.actions.electronicpayment.yappy.FINALIZE")
        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
    }
}
