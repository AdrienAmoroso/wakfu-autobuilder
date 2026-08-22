package me.chosante.ui.components

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Minimal RFC4180-style CSV encoder shared by every "export this table" button -- added first for
 * the Market screen's Prices tab, reusable as-is for any other table (just build a different
 * header/row list and call this). A UTF-8 BOM is prefixed so Excel renders accented names
 * correctly; it's still a completely standard, agent-readable UTF-8 CSV either way.
 */
fun toCsv(
    headers: List<String>,
    rows: List<List<String>>,
): String {
    val builder = StringBuilder("﻿")
    builder.append(headers.joinToString(",", transform = ::csvField)).append("\r\n")
    rows.forEach { row -> builder.append(row.joinToString(",", transform = ::csvField)).append("\r\n") }
    return builder.toString()
}

private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }

/**
 * Native "Save As" dialog for a CSV export, parented to [owner] (the real app window -- attached
 * once available, see `BuildSearchModel.attachWindow`, since the window doesn't exist yet when the
 * model is constructed) so it opens focused and properly modal. Returns the chosen [File], or null
 * if the user cancelled. Blocks the calling thread until the dialog closes, by AWT design -- call
 * it from a UI dispatcher, same as any other modal dialog.
 */
fun promptSaveCsvFile(
    owner: Frame?,
    suggestedFileName: String,
): File? {
    val dialog = FileDialog(owner, "Export CSV", FileDialog.SAVE)
    dialog.file = suggestedFileName
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val fileName = dialog.file ?: return null
    return File(directory, if (fileName.endsWith(".csv", ignoreCase = true)) fileName else "$fileName.csv")
}
