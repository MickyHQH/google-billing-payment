package com.mickyhqh.paymentmoduleproject

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mickyhqh.paymentmoduleproject.ui.greeting.Greeting
import com.mickyhqh.paymentmoduleproject.ui.store.StoreScreen
import com.mickyhqh.paymentmoduleproject.ui.theme.PaymentModuleProjectTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().apply {
            setKeepOnScreenCondition { mainViewModel.isSplashPending.value }
            setOnExitAnimationListener { splashScreenView ->
                splashScreenView.iconView.animate().alpha(0f).setDuration(1500)
                    .setListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                super.onAnimationEnd(animation)
                                splashScreenView.remove()
                            }
                        },
                    )
                    .setInterpolator(AccelerateInterpolator()).start()
            }
        }
        initInjection()
        initPaymentInstance()
        setContent {
            PaymentModuleProjectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(mainViewModel)
                }
            }
        }
    }

    private fun initInjection() {
        Injection.init(this)
    }

    private fun initPaymentInstance() {
        Injection.googleBillingPayment.addObserver(lifecycle)
    }

    @Composable
    private fun MainNavigation(
        mainViewModel: MainViewModel
    ) {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = Screen.Greeting.route) {
            composable(Screen.Greeting.route) {
                Greeting(
                    mainViewModel = mainViewModel,
                    onNavigateToStoreScreen = { navController.navigate(Screen.Store.route) }
                )
                BackHandler(enabled = true) {
                    navController.popBackStack()
                }
            }
            composable(Screen.Store.route) {
                StoreScreen(mainViewModel = mainViewModel) {
                    navController.popBackStack()
                }
                BackHandler(enabled = true) {
                    navController.popBackStack()
                }
            }
        }
    }

    sealed class Screen(val route: String) {
        object Store : Screen("Store")
        object Greeting : Screen("Greeting")
    }
}


/**
 * Find the closest Activity in a given Context.
 */
internal fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
//    throw IllegalStateException("Permissions should be called in the context of an Activity")
}