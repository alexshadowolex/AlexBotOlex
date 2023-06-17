package config

import java.io.File
import java.util.*

object GoogleSpreadSheetConfig {
    private val properties = Properties().apply {
        load(File("data\\properties\\googleSpreadSheetConfig.properties").inputStream())
    }

    val soundAlertSpreadSheetId: String = properties.getProperty("sound_alert_spread_sheet_id")
    val soundAlertSheetName: String = properties.getProperty("sound_alert_sheet_name")
    val soundAlertFirstDataCell: String = properties.getProperty("sound_alert_first_data_cell")
    val soundAlertLastDataCell: String = properties.getProperty("sound_alert_last_data_cell")
    val soundAlertSpreadSheetLink: String = properties.getProperty("sound_alert_spread_sheet_link")
    val soundAlertLastUpdatedCell: String = properties.getProperty("sound_alert_last_updated_cell")

    val commandListSpreadSheetId: String = properties.getProperty("command_list_spread_sheet_id")
    val commandListSheetName: String = properties.getProperty("command_list_sheet_name")
    val commandListFirstDataCell: String = properties.getProperty("command_list_first_data_cell")
    val commandListLastDataCell: String = properties.getProperty("command_list_last_data_cell")
    val commandListSpreadSheetLink: String = properties.getProperty("command_list_spread_sheet_link")
    val commandListLastUpdatedCell: String = properties.getProperty("command_list_last_updated_cell")
}