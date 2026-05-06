package com.robertochavez.timetracker.testing

import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.common.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.common.repository.WorkPresenceRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.common.repository.WorkSiteSessionRepository
import javax.inject.Inject

class SetupSnapshotSources @Inject constructor(
    val homeLocationRepository: HomeLocationRepository,
    val workLocationRepository: WorkLocationRepository,
    val workPresenceRepository: WorkPresenceRepository,
    val workSiteSessionRepository: WorkSiteSessionRepository,
    val workScheduleRepository: WorkScheduleRepository,
    val payPeriodSettingsRepository: PayPeriodSettingsRepository,
    val appSettingsRepository: AppSettingsRepository,
)
