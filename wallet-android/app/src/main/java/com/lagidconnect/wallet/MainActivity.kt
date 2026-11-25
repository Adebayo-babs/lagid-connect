package com.lagidconnect.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lagidconnect.wallet.ui.WalletNav
import com.lagidconnect.wallet.ui.theme.WalletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WalletTheme {
                WalletNav()
            }
        }
    }
}
