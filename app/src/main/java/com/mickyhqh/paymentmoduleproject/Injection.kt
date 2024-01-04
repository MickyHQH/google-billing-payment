package com.mickyhqh.paymentmoduleproject

import android.content.Context
import com.mickyhqh.googlebillingpayment.GoogleBillingPayment
import com.mickyhqh.googlebillingpayment.PaymentClient

object Injection {
    lateinit var googleBillingPayment : PaymentClient

    fun init(applicationContext: Context) {
        googleBillingPayment = GoogleBillingPayment.getInstance(
            applicationContext = applicationContext,
            listInAppProduct = listOf("hhh_100_coin"),
            listSubsProduct = listOf("group_premium"),
        )
    }
}