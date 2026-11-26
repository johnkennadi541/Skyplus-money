package com.example.domain.model

enum class PhaseDeliveryState {
  COMPLETE_IN_PROGRESS,
  PLANNED_NEXT,
  SCHEDULED,
}

data class RoadmapPhaseItem(
  val phaseNumber: Int,
  val title: String,
  val summary: String,
  val state: PhaseDeliveryState,
)

data class DomainModuleDescriptor(
  val name: String,
  val packagePath: String,
  val targetPhase: Int,
  val responsibility: String,
)
