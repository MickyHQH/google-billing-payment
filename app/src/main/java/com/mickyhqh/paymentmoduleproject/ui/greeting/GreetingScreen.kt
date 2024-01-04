package com.mickyhqh.paymentmoduleproject.ui.greeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import com.mickyhqh.paymentmoduleproject.MainViewModel
import com.mickyhqh.paymentmoduleproject.ui.common.TopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(
    mainViewModel: MainViewModel,
    onNavigateToStoreScreen: () -> Unit
) {
    val purchaseOneTimeItems by mainViewModel.purchaseOneTimeItems.collectAsState()
    val purchaseSubItems by mainViewModel.purchaseSubItems.collectAsState()
    Scaffold(
        topBar = {
            TopBar(title = "Greeting", canBack = false, onBack = {})
        }
    ) {
        Column (
            Modifier.fillMaxSize().padding(it),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "purchaseOneTimeItems: $purchaseOneTimeItems")
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = "purchaseSubItems: $purchaseSubItems")
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { onNavigateToStoreScreen() }) {
                Text(text = "Go to store")
            }
        }
    }
}
