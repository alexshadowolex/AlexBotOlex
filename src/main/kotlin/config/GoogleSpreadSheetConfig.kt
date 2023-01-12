package config

import java.io.File
import java.util.*

object GoogleSpreadSheetConfig {
    private val properties = Properties().apply {
        load(File("data\\googleSpreadSheetConfig.properties").inputStream())
    }

    val spreadSheetId: String = properties.getProperty("spread_sheet_id")
    val sheetName: String = properties.getProperty("sheet_name")
    val firstDataCell: String = properties.getProperty("first_data_cell")
    val lastDataCell: String = properties.getProperty("last_data_cell")
    val soundAlertSpreadSheetLink: String = properties.getProperty("sound_alert_spread_sheet_link")
    val lastUpdatedCell: String = properties.getProperty("last_updated_cell")
}