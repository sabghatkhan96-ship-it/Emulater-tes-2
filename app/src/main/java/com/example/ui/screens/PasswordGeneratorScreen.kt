package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.CryptoManager
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen(
    viewModel: VaultViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val password by viewModel.generatedPassword.collectAsStateWithLifecycle()
    val length by viewModel.generatorLength.collectAsStateWithLifecycle()
    val includeUpper by viewModel.generatorIncludeUpper.collectAsStateWithLifecycle()
    val includeLower by viewModel.generatorIncludeLower.collectAsStateWithLifecycle()
    val includeNumbers by viewModel.generatorIncludeNumbers.collectAsStateWithLifecycle()
    val includeSymbols by viewModel.generatorIncludeSymbols.collectAsStateWithLifecycle()

    val analysis = remember(password) { CryptoManager.analyzePassword(password) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password Generator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("generator_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Generated Password Display Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = password,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("generated_password_text")
                        )

                        Row {
                            IconButton(
                                onClick = { viewModel.regeneratePassword() },
                                modifier = Modifier.testTag("regenerate_password_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.copyToClipboard("Password", password, isSensitive = true)
                                },
                                modifier = Modifier.testTag("copy_generated_password_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Password",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    PasswordStrengthBar(analysis = analysis)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Password Length Slider
            Text(
                text = "Password Length: $length characters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = length.toFloat(),
                onValueChange = { viewModel.setGeneratorLength(it.toInt()) },
                valueRange = 8f..32f,
                steps = 23,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_length_slider")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Character Sets
            Text(
                text = "Character Rules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            GeneratorToggleRow(
                title = "Uppercase Letters (A-Z)",
                checked = includeUpper,
                onCheckedChange = { viewModel.toggleGeneratorUpper(it) }
            )
            GeneratorToggleRow(
                title = "Lowercase Letters (a-z)",
                checked = includeLower,
                onCheckedChange = { viewModel.toggleGeneratorLower(it) }
            )
            GeneratorToggleRow(
                title = "Numbers (0-9)",
                checked = includeNumbers,
                onCheckedChange = { viewModel.toggleGeneratorNumbers(it) }
            )
            GeneratorToggleRow(
                title = "Special Symbols (!@#$)",
                checked = includeSymbols,
                onCheckedChange = { viewModel.toggleGeneratorSymbols(it) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Alternative: Passphrase Mode
            OutlinedButton(
                onClick = { viewModel.generatePassphrase() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generate_passphrase_button")
            ) {
                Text("Generate Memorable Passphrase (e.g. beacon-nexus-umbra-42)")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GeneratorToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
