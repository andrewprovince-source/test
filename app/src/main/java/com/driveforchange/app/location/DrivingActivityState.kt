package com.driveforchange.app.location

/**
 * Whether a drive is currently in progress, according to Android's on-device activity
 * classifier. [LocationTrackingService] only counts distance while this is true, so walking,
 * cycling, or transit don't get credited as driving.
 *
 * Two sources write to this, and they are deliberately asymmetric:
 *
 * - [DriveTransitionReceiver] (primary) sets it on an `ENTER`/`EXIT` vehicle transition.
 * - [ActivityRecognitionReceiver] (backstop) may only *promote* it to true when it sees a
 *   confident `IN_VEHICLE` reading, never demote it. Periodic classifications report `STILL`
 *   at red lights and in traffic, so letting them demote would silently drop mileage
 *   mid-drive. A drive ends on an `EXIT` transition, or on the service's idle watchdog.
 *
 * Defaults to false: distance is only counted once driving has actively been confirmed.
 */
object DrivingActivityState {

    @Volatile
    var isInVehicle: Boolean = false
        private set

    /** An `ENTER_IN_VEHICLE` transition, or a confident periodic `IN_VEHICLE` classification. */
    fun markDrivingStarted() {
        isInVehicle = true
    }

    /** An `EXIT_IN_VEHICLE` transition, the idle watchdog, or tracking being torn down. */
    fun markDrivingStopped() {
        isInVehicle = false
    }
}
