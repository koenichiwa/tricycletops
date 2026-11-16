package com.koenichiwa.tricycletops.domain.usecase

import com.koenichiwa.tricycletops.domain.model.JoystickVector
import com.koenichiwa.tricycletops.domain.repository.VehicleControlRepository

class DriveVehicleUseCase(
    private val repository: VehicleControlRepository,
) {
    suspend operator fun invoke(vector: JoystickVector) {
        repository.drive(vector)
    }

    suspend fun stopImmediate() {
        repository.stopImmediate()
    }
}
