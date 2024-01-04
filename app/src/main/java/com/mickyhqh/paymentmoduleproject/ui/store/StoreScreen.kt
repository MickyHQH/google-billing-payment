package com.mickyhqh.paymentmoduleproject.ui.store

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mickyhqh.googlebillingpayment.model.PaymentItem
import com.mickyhqh.paymentmoduleproject.MainViewModel
import com.mickyhqh.paymentmoduleproject.findActivity
import com.mickyhqh.paymentmoduleproject.ui.common.TopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val onTimeItems by mainViewModel.onTimeItems.collectAsState()
    val subsItem by mainViewModel.subsItems.collectAsState()
    val onPaymentItemClick : (PaymentItem) -> Unit = { paymentItem ->
        activity?.let {
            mainViewModel.launchPurchase(it, paymentItem)
        }
    }
    Scaffold(
        topBar = {
            TopBar(title = "Store", onBack = onBack)
        }
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(10.dp),
        ) {
            Text(text = "IN APP ITEMS")
            Column(
                Modifier
                    .fillMaxWidth(.6f)
                    .align(Alignment.CenterHorizontally)
            ) {
                onTimeItems?.map { PaymentItem(it) { onPaymentItemClick(it) } }?.toList()
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "SUBS ITEMS")
            Column(
                Modifier
                    .fillMaxWidth(.6f)
                    .align(Alignment.CenterHorizontally)
            ) {
                subsItem?.map { listSub -> listSub.forEach { PaymentItem(it) { onPaymentItemClick(it) } } }?.toList()
            }
        }
    }
}

@Composable
fun PaymentItem(paymentItem: PaymentItem, onClick: () -> Unit) {
    Button(
        modifier = Modifier
            .padding(5.dp),
        onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = paymentItem.basePlanId ?: "No-name")
            Text(modifier = Modifier.align(Alignment.End), text = paymentItem.formattedPrice ?: "No-price")
        }
    }
}
