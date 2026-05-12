package com.robertochavez.timetracker.core.designsystem

object TimeTrackerTestTags {
    const val APP_ROOT = "time_tracker_app"
    const val BOTTOM_NAV = "bottom_nav"
    const val STARTUP_SETUP_DIALOG = "startup_setup_dialog"
    const val STARTUP_ENABLE_BUTTON = "startup_enable_button"
    const val STARTUP_BACKGROUND_BUTTON = "startup_background_button"

    const val DASHBOARD_SCREEN = "screen_dashboard"
    const val HOME_SCREEN = "screen_places"
    const val TRACKING_SCREEN = "screen_tracking"
    const val SETTINGS_SCREEN = "screen_settings"

    const val HOME_USE_CURRENT_BUTTON = "home_use_current_button"
    const val HOME_LATITUDE_FIELD = "home_latitude_field"
    const val HOME_LONGITUDE_FIELD = "home_longitude_field"
    const val HOME_RADIUS_FIELD = "home_radius_field"
    const val HOME_EDIT_PIN_BUTTON = "home_edit_pin_button"
    const val HOME_CANCEL_EDIT_BUTTON = "home_cancel_edit_button"
    const val HOME_SAVE_PIN_BUTTON = "home_save_pin_button"
    const val HOME_SAVE_RADIUS_BUTTON = "home_save_radius_button"
    const val HOME_OVERWRITE_CONFIRM_BUTTON = "home_overwrite_confirm_button"
    const val HOME_OVERWRITE_CANCEL_BUTTON = "home_overwrite_cancel_button"
    const val WORK_USE_CURRENT_BUTTON = "work_use_current_button"
    const val WORK_LABEL_FIELD = "work_label_field"
    const val WORK_LATITUDE_FIELD = "work_latitude_field"
    const val WORK_LONGITUDE_FIELD = "work_longitude_field"
    const val WORK_RADIUS_FIELD = "work_radius_field"
    const val WORK_EDIT_PIN_BUTTON = "work_edit_pin_button"
    const val WORK_CANCEL_EDIT_BUTTON = "work_cancel_edit_button"
    const val WORK_SAVE_PIN_BUTTON = "work_save_pin_button"
    const val WORK_SAVE_RADIUS_BUTTON = "work_save_radius_button"
    const val WORK_ADD_LOCATION_BUTTON = "work_add_location_button"
    const val WORK_REPLACE_LOCATION_BUTTON = "work_replace_location_button"
    const val WORK_SAVE_CANCEL_BUTTON = "work_save_cancel_button"

    const val TRACKING_START_BUTTON = "tracking_start_button"
    const val TRACKING_STOP_BUTTON = "tracking_stop_button"

    const val DASHBOARD_SUMMARY_PANEL = "dashboard_summary_panel"
    const val DASHBOARD_WEEKLY_LEDGER = "dashboard_weekly_ledger"

    const val SETTINGS_REQUEST_FOREGROUND_BUTTON = "settings_request_foreground_button"
    const val SETTINGS_ENABLE_BACKGROUND_BUTTON = "settings_enable_background_button"
    const val SETTINGS_ENABLE_ACTIVITY_BUTTON = "settings_enable_activity_button"
    const val SETTINGS_DISABLE_ACTIVITY_BUTTON = "settings_disable_activity_button"
    const val SETTINGS_ANCHOR_DATE_FIELD = "settings_anchor_date_field"
    const val SETTINGS_SAVE_ANCHOR_BUTTON = "settings_save_anchor_button"
    const val SETTINGS_MINIMAL_NOTIFICATION_SWITCH = "settings_minimal_notification_switch"
    const val SETTINGS_LIVE_TIMER_NOTIFICATION_SWITCH = "settings_live_timer_notification_switch"
    const val SETTINGS_PRIVACY_DISCLOSURE_SWITCH = "settings_privacy_disclosure_switch"
    const val SETTINGS_DELETE_LOCAL_DATA_BUTTON = "settings_delete_local_data_button"
    const val SETTINGS_DELETE_CONFIRM_DIALOG = "settings_delete_confirm_dialog"
    const val SETTINGS_DELETE_CONFIRM_CANCEL_BUTTON = "settings_delete_confirm_cancel_button"
    const val SETTINGS_DELETE_CONFIRM_BUTTON = "settings_delete_confirm_button"

    fun navItem(route: String): String = "nav_$route"

    fun radiusOption(prefix: String, suffix: String): String = "${prefix}_radius_option_$suffix"

    fun workdaySwitch(dayName: String): String = "settings_workday_${dayName.lowercase()}_switch"

    fun trackingSessionCard(idPrefix: String): String = "tracking_session_${idPrefix}_card"

    fun trackingSessionCountsSwitch(idPrefix: String): String = "tracking_session_${idPrefix}_counts_switch"

    fun trackingSessionEditButton(idPrefix: String): String = "tracking_session_${idPrefix}_edit_button"

    fun trackingSessionCancelEditButton(idPrefix: String): String = "tracking_session_${idPrefix}_cancel_edit_button"

    fun trackingSessionStartField(idPrefix: String): String = "tracking_session_${idPrefix}_start_field"

    fun trackingSessionEndField(idPrefix: String): String = "tracking_session_${idPrefix}_end_field"

    fun trackingSessionMilesField(idPrefix: String): String = "tracking_session_${idPrefix}_miles_field"

    fun trackingSessionSaveButton(idPrefix: String): String = "tracking_session_${idPrefix}_save_button"
}
