package com.example.myproducts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myproducts.ui.navigation.AppNavHost
import com.example.myproducts.ui.theme.MyProductsTheme
import dagger.hilt.android.AndroidEntryPoint

/** The app's single Activity. Screens are Compose destinations inside AppNavHost. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyProductsTheme {
                AppNavHost()
            }
        }
    }
}
