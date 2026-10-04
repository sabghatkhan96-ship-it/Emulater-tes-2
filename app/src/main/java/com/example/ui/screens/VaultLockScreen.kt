package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.security.BiometricAuthManager
import com.example.ui.theme.VaultDarkBackground
import com.example.ui.theme.VaultDarkSurfaceVariant
import com.example.ui.theme.VaultSuccess
import com.example.ui.viewmodel.VaultViewModel

@Composable
fun VaultLockScreen(
    viewModel: VaultViewModel,
    isSetupMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var enteredPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmingSetup by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val pinLength = 6

    // Attempt automatic biometric prompt if enabled and not initial setup
    LaunchedEffect(isSetupMode) {
        if (!isSetupMode && viewModel.securityPrefs.isBiometricEnabled && activity != null) {
            val status = BiometricAuthManager.checkBiometricAvailability(context)
            if (status == BiometricAuthManager.BiometricStatus.AVAILABLE) {
                BiometricAuthManager.showBiometricPrompt(
                    activity = activity,
                    onSuccess = { viewModel.unlockWithBiometricSuccess() },
                    onError = { /* Keep fallback to PIN */ }
                )
            }
        }
    }

    fun handleDigit(digit: String) {
        localError = null
        if (enteredPin.length < pinLength) {
            val newPin = enteredPin + digit
            enteredPin = newPin

            if (newPin.length == pinLength) {
                if (isSetupMode) {
                    if (!isConfirmingSetup) {
                        confirmPin = newPin
                        enteredPin = ""
                        isConfirmingSetup = true
                    } else {
                        if (newPin == confirmPin) {
                            viewModel.setupMasterPin(newPin)
                        } else {
                            localError = "PINs do not match. Please restart setup."
                            enteredPin = ""
                            confirmPin = ""
                            isConfirmingSetup = false
                        }
                    }
                } else {
                    val success = viewModel.unlockWithPin(newPin)
                    if (!success) {
                        enteredPin = ""
                        localError = "Incorrect PIN. Please try again."
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            localError = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Header Icon and Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Vault Security",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "VaultPass",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "🔒 Hardware AES-256 GCM Protected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = when {
                        isSetupMode && !isConfirmingSetup -> "Create 6-Digit Master PIN"
                        isSetupMode && isConfirmingSetup -> "Confirm 6-Digit Master PIN"
                        else -> "Enter Master PIN to Unlock Vault"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isSetupMode) "This PIN protects all passwords on your device" else "Credentials remain securely encrypted at rest",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until pinLength) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                AnimatedVisibility(
                    visible = localError != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    localError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }

            // Keypad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                for (row in rows) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        for (digit in row) {
                            KeypadButton(
                                text = digit,
                                onClick = { handleDigit(digit) },
                                modifier = Modifier.testTag("pin_key_$digit")
                            )
                        }
                    }
                }

                // Bottom row: Biometric / Reset, 0, Backspace
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Biometric or clear
                    Box(
                        modifier = Modifier
                            .size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isSetupMode && activity != null) {
                            IconButton(
                                onClick = {
                                    BiometricAuthManager.showBiometricPrompt(
                                        activity = activity,
                                        onSuccess = { viewModel.unlockWithBiometricSuccess() },
                                        onError = { localError = it }
                                    )
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("biometric_unlock_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Unlock with Biometrics",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        } else if (isSetupMode && isConfirmingSetup) {
                            TextButton(
                                onClick = {
                                    isConfirmingSetup = false
                                    enteredPin = ""
                                    confirmPin = ""
                                }
                            ) {
                                Text("Back", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    KeypadButton(
                        text = "0",
                        onClick = { handleDigit("0") },
                        modifier = Modifier.testTag("pin_key_0")
                    )

                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { handleBackspace() },
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("pin_backspace_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Backspace",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.size(72.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
