package com.example.yp_qr.intenthandlers

import android.app.Activity
import android.content.Intent

class GetBehaviorHandler(private val activity: Activity) {

    fun handle() {
        val result = Intent("icg.actions.electronicpayment.tefbanesco.GET_BEHAVIOR").apply {
            putExtra("SupportsTransactionVoid", false)
            putExtra("SupportsTransactionQuery", false)
            putExtra("SupportsNegativeSales", false)
            putExtra("SupportsPartialRefund", false)
            putExtra("SupportsBatchClose", false)
            putExtra("SupportsTipAdjustment", false)
            putExtra("OnlyCreditForTipAdjustment", true)
            putExtra("SupportsCredit", true)
            putExtra("SupportsDebit", true)
            putExtra("SupportsEBTFoodstamp", false)
            putExtra("HasCustomParams", true) // Si usas nombre/logo personalizado
        }

        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
    }
}
