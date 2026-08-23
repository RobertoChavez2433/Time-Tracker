package com.robertochavez.timetracker.core.designsystem

@Suppress("TooManyFunctions")
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

    const val PLACES_SAVED_LIST = "places_saved_list"
    const val ADD_PLACE_BUTTON = "add_place_button"
    const val ADD_PLACE_FLOW = "add_place_flow"
    const val ADD_PLACE_CURRENT_BUTTON = "add_place_current_button"
    const val ADD_PLACE_MAP_BUTTON = "add_place_map_button"
    const val ADD_PLACE_ADDRESS_BUTTON = "add_place_address_button"
    const val HOME_USE_CURRENT_BUTTON = "home_use_current_button"
    const val HOME_LATITUDE_FIELD = "home_latitude_field"
    const val HOME_LONGITUDE_FIELD = "home_longitude_field"
    const val HOME_RADIUS_FIELD = "home_radius_field"
    const val HOME_EDIT_PIN_BUTTON = "home_edit_pin_button"
    const val HOME_EDIT_FLOW = "home_edit_flow"
    const val HOME_EDIT_CURRENT_BUTTON = "home_edit_current_button"
    const val HOME_EDIT_MAP_BUTTON = "home_edit_map_button"
    const val HOME_EDIT_ADDRESS_BUTTON = "home_edit_address_button"
    const val HOME_ADDRESS_FIELD = "home_address_field"
    const val HOME_RESOLVE_ADDRESS_BUTTON = "home_resolve_address_button"
    const val HOME_OPEN_MAP_BUTTON = "home_open_map_button"
    const val HOME_CANCEL_EDIT_BUTTON = "home_cancel_edit_button"
    const val HOME_SAVE_PIN_BUTTON = "home_save_pin_button"
    const val HOME_SAVE_RADIUS_BUTTON = "home_save_radius_button"
    const val HOME_OVERWRITE_CONFIRM_BUTTON = "home_overwrite_confirm_button"
    const val HOME_OVERWRITE_CANCEL_BUTTON = "home_overwrite_cancel_button"
    const val HOME_MAP_PICKER = "home_map_picker"
    const val HOME_MAP_CANCEL_BUTTON = "home_map_cancel_button"
    const val HOME_MAP_CONFIRM_BUTTON = "home_map_confirm_button"
    const val WORK_USE_CURRENT_BUTTON = "work_use_current_button"
    const val WORK_LABEL_FIELD = "work_label_field"
    const val WORK_ADDRESS_FIELD = "work_address_field"
    const val WORK_RESOLVE_ADDRESS_BUTTON = "work_resolve_address_button"
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
    const val WORK_OPEN_MAP_BUTTON = "work_open_map_button"
    const val WORK_MAP_DISCLOSURE_DIALOG = "work_map_disclosure_dialog"
    const val WORK_MAP_DISCLOSURE_ACCEPT_BUTTON = "work_map_disclosure_accept_button"
    const val WORK_MAP_DISCLOSURE_DECLINE_BUTTON = "work_map_disclosure_decline_button"
    const val WORK_MAP_PICKER = "work_map_picker"
    const val WORK_MAP_CANCEL_BUTTON = "work_map_cancel_button"
    const val WORK_MAP_CONFIRM_BUTTON = "work_map_confirm_button"

    const val TRACKING_START_BUTTON = "tracking_start_button"
    const val TRACKING_STOP_BUTTON = "tracking_stop_button"

    const val DASHBOARD_SUMMARY_PANEL = "dashboard_summary_panel"
    const val DASHBOARD_WEEKLY_LEDGER = "dashboard_weekly_ledger"
    const val DASHBOARD_PREVIOUS_WEEK_BUTTON = "dashboard_previous_week_button"
    const val DASHBOARD_NEXT_WEEK_BUTTON = "dashboard_next_week_button"
    const val DASHBOARD_EDIT_WORKDAYS_BUTTON = "dashboard_edit_workdays_button"

    const val SETTINGS_REQUEST_FOREGROUND_BUTTON = "settings_request_foreground_button"
    const val SETTINGS_ENABLE_BACKGROUND_BUTTON = "settings_enable_background_button"
    const val SETTINGS_ENABLE_ACTIVITY_BUTTON = "settings_enable_activity_button"
    const val SETTINGS_DISABLE_ACTIVITY_BUTTON = "settings_disable_activity_button"
    const val SETTINGS_ANCHOR_DATE_FIELD = "settings_anchor_date_field"
    const val SETTINGS_SAVE_ANCHOR_BUTTON = "settings_save_anchor_button"
    const val SETTINGS_WEEKLY_CADENCE_BUTTON = "settings_weekly_cadence_button"
    const val SETTINGS_BIWEEKLY_CADENCE_BUTTON = "settings_biweekly_cadence_button"
    const val SETTINGS_PAY_PERIOD_PREVIEW = "settings_pay_period_preview"
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

    fun savedPlaceItem(id: String): String = "saved_place_${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}"

    fun trackingSessionCard(idPrefix: String): String = "tracking_session_${idPrefix}_card"

    fun trackingSessionCountsSwitch(idPrefix: String): String = "tracking_session_${idPrefix}_counts_switch"

    fun trackingSessionEditButton(idPrefix: String): String = "tracking_session_${idPrefix}_edit_button"

    fun trackingSessionCancelEditButton(idPrefix: String): String = "tracking_session_${idPrefix}_cancel_edit_button"

    fun trackingSessionStartField(idPrefix: String): String = "tracking_session_${idPrefix}_start_field"

    fun trackingSessionEndField(idPrefix: String): String = "tracking_session_${idPrefix}_end_field"

    fun trackingSessionMilesField(idPrefix: String): String = "tracking_session_${idPrefix}_miles_field"

    fun trackingSessionSaveButton(idPrefix: String): String = "tracking_session_${idPrefix}_save_button"
}
