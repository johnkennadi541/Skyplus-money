package com.example.feature.dashboard

import com.example.domain.model.FinancialReportSnapshot
import com.example.domain.model.ReportDateWindow
import com.example.domain.usecase.ObserveFinancialReportsUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Thin seam between the Home dashboard and the existing
 * [ObserveFinancialReportsUseCase].
 *
 * It exists so the ViewModel can be unit tested with a fake snapshot without
 * touching the domain layer: the use case stays final and unmodified, and
 * [ObserveReportsHomeSource] simply delegates to it unchanged.
 */
interface HomeFinancialReportsSource {
  operator fun invoke(
    userId: String,
    windowFlow: Flow<ReportDateWindow>,
  ): Flow<FinancialReportSnapshot>
}

@Singleton
class ObserveReportsHomeSource
@Inject
constructor(private val observeFinancialReportsUseCase: ObserveFinancialReportsUseCase) :
  HomeFinancialReportsSource {

  override fun invoke(
    userId: String,
    windowFlow: Flow<ReportDateWindow>,
  ): Flow<FinancialReportSnapshot> = observeFinancialReportsUseCase(userId, windowFlow)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeDashboardModule {

  @Binds
  @Singleton
  abstract fun bindHomeFinancialReportsSource(
    impl: ObserveReportsHomeSource
  ): HomeFinancialReportsSource
}
