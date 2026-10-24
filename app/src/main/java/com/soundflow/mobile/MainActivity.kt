package com.soundflow.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.soundflow.mobile.data.LocalStore
import com.soundflow.mobile.navigation.SoundFlowNavHost
import com.soundflow.mobile.ui.theme.SoundFlowTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    var sharedUrlFromIntent: String? = null
        private set

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restaure la langue choisie par l'utilisateur au démarrage
        lifecycleScope.launch {
            val store = LocalStore(this@MainActivity)
            val savedLanguage = store.loadLanguageTag()
            if (!savedLanguage.isNull_or_empty()) {
                val currentLocales = AppCompatDelegate.getApplicationLocales()
                if (currentLocales.isEmpty || currentLocales.get(0)?.language != savedLanguage) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLanguage))
                }
            }
        }

        handleIncomingIntent(intent)

        setContent {
            SoundFlowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SoundFlowNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val action = intent?.action
        val data = intent?.dataString
        val extraText = intent?.getStringExtra(Intent.EXTRA_TEXT)

        sharedUrlFromIntent = when {
            data != null && (data.contains("soundflow.app/playlist") || data.contains("soundflow://playlist")) -> data
            extraText != null && (extraText.contains("soundflow.app/playlist") || extraText.contains("soundflow://playlist")) -> extraText
            else -> null
        }
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.isEmpty()
}
