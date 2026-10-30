package com.example.mobilereader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mobilereader.R
import com.example.mobilereader.data.translation.ContextualTranslationProblem
import com.example.mobilereader.data.translation.ContextualTranslationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualTranslationResultSheet(
    state: ContextualTranslationState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.contextual_translation_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            val selectedText = when (state) {
                ContextualTranslationState.Idle -> ""
                is ContextualTranslationState.Loading -> state.selectedText
                is ContextualTranslationState.Success -> state.selectedText
                is ContextualTranslationState.Failure -> state.selectedText
            }
            if (selectedText.isNotBlank()) {
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            when (state) {
                ContextualTranslationState.Idle -> Unit
                is ContextualTranslationState.Loading -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.contextual_translation_loading))
                }
                is ContextualTranslationState.Success -> {
                    when (val result = state.result) {
                        ContextualTranslationResult.Demonstration -> {
                            Text(
                                text = stringResource(R.string.contextual_translation_demonstration),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(stringResource(R.string.contextual_translation_demo_translation))
                            Text(stringResource(R.string.contextual_translation_demo_explanation))
                        }
                        is ContextualTranslationResult.Complete -> {
                            Text(
                                text = stringResource(R.string.contextual_translation_result_label),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(result.response.translation)
                            Text(
                                text = stringResource(R.string.contextual_translation_explanation_label),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(result.response.explanation)
                            result.response.nuance?.takeIf(String::isNotBlank)?.let { nuance ->
                                Text(
                                    text = stringResource(R.string.contextual_translation_nuance_label),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(nuance)
                            }
                        }
                    }
                }
                is ContextualTranslationState.Failure -> {
                    Text(
                        text = contextualTranslationProblemText(state),
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.contextual_translation_retry))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun contextualTranslationProblemText(state: ContextualTranslationState.Failure): String =
    when (state.problem) {
        ContextualTranslationProblem.EMPTY_BOOK ->
            stringResource(R.string.contextual_translation_empty_book)
        ContextualTranslationProblem.INVALID_SELECTION ->
            stringResource(R.string.contextual_translation_invalid_selection)
        ContextualTranslationProblem.EMPTY_SELECTION ->
            stringResource(R.string.contextual_translation_empty_selection)
        ContextualTranslationProblem.SELECTED_TEXT_TOO_LONG ->
            stringResource(R.string.contextual_translation_selection_too_long)
        ContextualTranslationProblem.PARAGRAPH_TOO_LONG ->
            stringResource(R.string.contextual_translation_paragraph_too_long)
        ContextualTranslationProblem.INVALID_SERVER_ADDRESS ->
            stringResource(R.string.contextual_translation_invalid_server_address)
        ContextualTranslationProblem.UNENCRYPTED_CONNECTION ->
            stringResource(R.string.contextual_translation_https_required)
        ContextualTranslationProblem.SERVER_RESPONSE ->
            stringResource(R.string.contextual_translation_server_response, state.statusCode ?: 0)
        ContextualTranslationProblem.INVALID_RESPONSE ->
            stringResource(R.string.contextual_translation_invalid_response)
        ContextualTranslationProblem.NETWORK ->
            stringResource(R.string.contextual_translation_network_error)
    }
