package com.bravoscribe.android.ui.auth.register

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bravoscribe.android.domain.model.User

@Composable
fun RegisterScreen(
    onRegisterSuccess: (User) -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Register")
    }
}
