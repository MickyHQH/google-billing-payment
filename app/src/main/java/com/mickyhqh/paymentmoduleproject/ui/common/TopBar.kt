package com.mickyhqh.paymentmoduleproject.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mickyhqh.paymentmoduleproject.ui.theme.PaymentModuleProjectTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(title: String, canBack: Boolean = true, onBack: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            if (canBack) {
                IconButton(onClick = { onBack() }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Localized description"
                    )
                }
            }
        },
        title = {
            Text(text = title)
        })
}

@Preview(showBackground = true)
@Composable
fun TopBarPreview() {
    PaymentModuleProjectTheme {
        TopBar("HUY") {}
    }
}