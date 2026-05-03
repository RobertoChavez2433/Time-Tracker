package com.robertochavez.timetracker.core.designsystem

object TimeTrackerTestTags {
    const val APP_ROOT = "time_tracker_app"
    const val BOTTOM_NAV = "bottom_nav"

    const val HOME_SCREEN = "screen_home"
    const val TRACKING_SCREEN = "screen_tracking"
    const val REPORTS_SCREEN = "screen_reports"
    const val SETTINGS_SCREEN = "screen_settings"

    const val HOME_USE_CURRENT_BUTTON = "home_use_current_button"
    const val HOME_LATITUDE_FIELD = "home_latitude_field"
    const val HOME_LONGITUDE_FIELD = "home_longitude_field"
    const val HOME_RADIUS_FIELD = "home_radius_field"
    const val HOME_SAVE_PIN_BUTTON = "home_save_pin_button"
    const val WORK_USE_CURRENT_BUTTON = "work_use_current_button"
    const val WORK_LATITUDE_FIELD = "work_latitude_field"
    const val WORK_LONGITUDE_FIELD = "work_longitude_field"
    const val WORK_RADIUS_FIELD = "work_radius_field"
    const val WORK_SAVE_PIN_BUTTON = "work_save_pin_button"

    const val TRACKING_START_BUTTON = "tracking_start_button"
    const val TRACKING_STOP_BUTTON = "tracking_stop_button"

    const val REPORTS_TODAY_CARD = "reports_today_card"
    const val REPORTS_WEEKLY_CARD = "reports_weekly_card"
    const val REPORTS_BIWEEKLY_CARD = "reports_biweekly_card"
    const val REPORTS_MONTHLY_CARD = "reports_monthly_card"
    const val REPORTS_YEARLY_CARD = "reports_yearly_card"

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

    fun workdaySwitch(dayName: String): String = "settings_workday_${dayName.lowercase()}_switch"

    fun trackingSessionCard(idPrefix: String): String = "tracking_session_${idPrefix}_card"

    fun trackingSessionCountsSwitch(idPrefix: String): String = "tracking_session_${idPrefix}_counts_switch"

    fun trackingSessionStartField(idPrefix: String): String = "tracking_session_${idPrefix}_start_field"

    fun trackingSessionEndField(idPrefix: String): String = "tracking_session_${idPrefix}_end_field"

    fun trackingSessionMilesField(idPrefix: String): String = "tracking_session_${idPrefix}_miles_field"

    fun trackingSessionSaveButton(idPrefix: String): String = "tracking_session_${idPrefix}_save_button"
}
