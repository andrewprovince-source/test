package com.driveforchange.app.location

/**
 * Latest verdict from Android's on-device activity classifier on whether the user is
 * currently in a vehicle. [LocationTrackingService] only counts distance while this is true,
 * so walking, cycling, or transit don't get credited as driving. Defaults to false: distance
 * is only counted once the classifier has actively confirmed driving, never before.
 */
object DrivingActivityState {
    @Volatile
    var isInVehicle: Boolean = false
        internal set
}
