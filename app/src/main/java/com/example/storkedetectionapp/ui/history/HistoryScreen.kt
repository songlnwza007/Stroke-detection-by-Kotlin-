package com.example.storkedetectionapp.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.storkedetectionapp.data.FacialDetectionEntity
import com.example.storkedetectionapp.data.HandExerciseEntity
import com.example.storkedetectionapp.viewmodel.HistoryViewModel
import com.example.storkedetectionapp.viewmodel.RecordFilter

// ... rest of the file stays the same
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Buttons
            FilterButtons(
                currentFilter = uiState.filter,
                onFilterChange = { viewModel.setFilter(it) }
            )

            // Statistics
            StatisticsBar(
                facialCount = uiState.facialCount,
                handCount = uiState.handCount,
                strokeCount = uiState.strokeCount
            )

            // Records List
            RecordsList(
                facialDetections = uiState.facialDetections,
                handExercises = uiState.handExercises,
                filter = uiState.filter,
                onDeleteFacial = { viewModel.deleteFacialDetection(it) },
                onDeleteHand = { viewModel.deleteHandExercise(it) }
            )
        }
    }
}

@Composable
fun FilterButtons(
    currentFilter: RecordFilter,
    onFilterChange: (RecordFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentFilter == RecordFilter.ALL,
            onClick = { onFilterChange(RecordFilter.ALL) },
            label = { Text("All") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = currentFilter == RecordFilter.FACIAL,
            onClick = { onFilterChange(RecordFilter.FACIAL) },
            label = { Text("Facial") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = currentFilter == RecordFilter.HAND,
            onClick = { onFilterChange(RecordFilter.HAND) },
            label = { Text("Hand") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatisticsBar(
    facialCount: Int,
    handCount: Int,
    strokeCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Facial", facialCount.toString())
            StatItem("Hand", handCount.toString())
            StatItem("Alerts", strokeCount.toString())
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RecordsList(
    facialDetections: List<FacialDetectionEntity>,
    handExercises: List<HandExerciseEntity>,
    filter: RecordFilter,
    onDeleteFacial: (FacialDetectionEntity) -> Unit,
    onDeleteHand: (HandExerciseEntity) -> Unit
) {
    val showFacial = filter == RecordFilter.ALL || filter == RecordFilter.FACIAL
    val showHand = filter == RecordFilter.ALL || filter == RecordFilter.HAND

    val isEmpty = when (filter) {
        RecordFilter.ALL -> facialDetections.isEmpty() && handExercises.isEmpty()
        RecordFilter.FACIAL -> facialDetections.isEmpty()
        RecordFilter.HAND -> handExercises.isEmpty()
    }

    if (isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No records found.\nComplete a test to see results here!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showFacial) {
                items(
                    items = facialDetections,
                    key = { "facial_${it.id}" }
                ) { detection ->
                    FacialRecordCard(
                        detection = detection,
                        onDelete = { onDeleteFacial(detection) }
                    )
                }
            }

            if (showHand) {
                items(
                    items = handExercises,
                    key = { "hand_${it.id}" }
                ) { exercise ->
                    HandRecordCard(
                        exercise = exercise,
                        onDelete = { onDeleteHand(exercise) }
                    )
                }
            }
        }
    }
}