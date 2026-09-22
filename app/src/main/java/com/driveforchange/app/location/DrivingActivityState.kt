package com.driveforchange.app.location

/**
 * Whether the activity classifier currently confirms the user is driving.
 * [LocationTrackingService] only credits distance while this is true, so walking, cycling,
 * or transit don't get counted as driving.
 *
 * Set by an `ENTER` vehicle transition or a confident periodic `IN_VEHICLE` reading. Cleared
 * by an `EXIT` transition, a confident on-foot reading, or the service stopping — never by a
 * `STILL` reading, since that's what the classifier reports at red lights.
 *
 * Defaults to false: distance is only credited once driving has actively been confirmed.
 */
object DrivingActivityState {

    @Volatile
    var isInVehicle: Boolean = false
        private set

    fun markDrivingStarted() {
        isInVehicle = true
    }

    fun markDrivingStopped() {
        isInVehicle = false
    }
}
