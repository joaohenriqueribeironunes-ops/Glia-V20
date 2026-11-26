package com.glia.assistant.tools

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import org.json.JSONObject

data class ToolCall(
    val name: String,
    val args: JSONObject = JSONObject()
)

interface DeviceController {
    fun currentTimeMillis(): Long
    fun flashlightOn(): Boolean?
    fun setFlashlight(on: Boolean): Boolean
    fun bluetoothOn(): Boolean?
    fun setBluetooth(on: Boolean): Boolean
    fun wifiOn(): Boolean?
    fun setWifi(on: Boolean): Boolean
}

class DefaultDeviceController : DeviceController {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun flashlightOn(): Boolean? = false
    override fun setFlashlight(on: Boolean): Boolean = true
    override fun bluetoothOn(): Boolean? = false
    override fun setBluetooth(on: Boolean): Boolean = true
    override fun wifiOn(): Boolean? = false
    override fun setWifi(on: Boolean): Boolean = true
}

class ContextDeviceController(private val context: Context) : DeviceController {
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var torchOn = false
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    private val wifiManager: WifiManager? by lazy {
        context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    init {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        cameraId = cameraManager?.let { runCatching { it.cameraIdList.firstOrNull() }.getOrNull() }
    }

    private fun canUseBluetooth(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun flashlightOn(): Boolean = torchOn

    override fun setFlashlight(on: Boolean): Boolean {
        val cm = cameraManager ?: return false
        val id = cameraId ?: return false
        return try {
            cm.setTorchMode(id, on)
            torchOn = on
            true
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun bluetoothOn(): Boolean? {
        val adapter = bluetoothAdapter ?: return null
        if (!canUseBluetooth()) return null
        return try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun setBluetooth(on: Boolean): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!canUseBluetooth()) return false
        return try {
            if (on) adapter.enable() else adapter.disable()
        } catch (_: SecurityException) {
            false
        }
    }

    @Suppress("DEPRECATION")
    override fun wifiOn(): Boolean? = try {
        wifiManager?.isWifiEnabled
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    override fun setWifi(on: Boolean): Boolean = try {
        val wm = wifiManager ?: return false
        wm.isWifiEnabled = on
        true
    } catch (_: Exception) {
        false
    }
}

class HoraTool : GliaTool {
    override val name: String = "hora_atual"
    override val description: String = "Retorna a hora atual do dispositivo."
    override val inputSchemaJson: String = """{"type":"object","properties":{},"required":["tool"]}"""

    override fun execute(args: JSONObject): ToolResult {
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault())
        val spoken = "Agora sao ${now.hour.toString().padStart(2, '0')} horas e ${now.minute.toString().padStart(2, '0')} minutos."
        return ToolResult(true, name, spoken, JSONObject().put("tool", name))
    }
}

class DataTool : GliaTool {
    override val name: String = "data_atual"
    override val description: String = "Retorna a data atual do dispositivo."
    override val inputSchemaJson: String = """{"type":"object","properties":{},"required":["tool"]}"""

    override fun execute(args: JSONObject): ToolResult {
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault())
        val spoken = "Hoje e dia ${now.dayOfMonth} de ${now.month.name} de ${now.year}."
        return ToolResult(true, name, spoken, JSONObject().put("tool", name))
    }
}

class DiaDaSemanaTool : GliaTool {
    override val name: String = "dia_da_semana"
    override val description: String = "Retorna o dia da semana de uma data informada."
    override val inputSchemaJson: String = """{"type":"object","properties":{"day":{"type":"integer"},"month":{"type":"integer"},"year":{"type":"integer"}},"required":["tool","day"]}"""

    override fun execute(args: JSONObject): ToolResult {
        val day = args.optInt("day")
        val month = args.optInt("month")
        val year = args.optInt("year")
        if (day < 1 || day > 31 || month < 1 || month > 12 || year < 1900 || year > 2100) {
            return ToolResult(false, name, "Data invalida.", JSONObject().put("tool", name))
        }
        val date = LocalDate.of(year, month, day)
        val spoken = "O dia ${date.dayOfMonth} de ${date.month.name} de ${date.year} cai numa ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))}."
        return ToolResult(true, name, spoken, JSONObject().put("tool", name))
    }
}

class LanternaTool(private val device: DeviceController) : GliaTool {
    override val name: String = "lanterna"
    override val description: String = "Liga ou desliga a lanterna do aparelho."
    override val inputSchemaJson: String = """{"type":"object","properties":{"action":{"type":"string"}},"required":["tool","action"]}"""

    override fun execute(args: JSONObject): ToolResult {
        val action = args.optString("action").ifBlank { "toggle" }
        val on = when (action) {
            "on" -> true
            "off" -> false
            "toggle" -> (device.flashlightOn() ?: false).not()
            else -> return ToolResult(false, name, "Acao invalida para lanterna.", JSONObject().put("tool", name))
        }
        val ok = device.setFlashlight(on)
        val spoken = if (ok) "Lanterna ${if (on) "ligada" else "desligada"}." else "Nao foi possivel controlar a lanterna agora."
        return ToolResult(ok, name, spoken, JSONObject().put("tool", name))
    }
}

class BluetoothTool(private val device: DeviceController) : GliaTool {
    override val name: String = "bluetooth"
    override val description: String = "Liga ou desliga o Bluetooth."
    override val inputSchemaJson: String = """{"type":"object","properties":{"action":{"type":"string"}},"required":["tool","action"]}"""

    override fun execute(args: JSONObject): ToolResult {
        val action = args.optString("action").ifBlank { "status" }
        return when (action) {
            "on" -> {
                val ok = device.setBluetooth(true)
                ToolResult(ok, name, if (ok) "Bluetooth ligado." else "Nao foi possivel ligar o Bluetooth agora.")
            }
            "off" -> {
                val ok = device.setBluetooth(false)
                ToolResult(ok, name, if (ok) "Bluetooth desligado." else "Nao foi possivel desligar o Bluetooth agora.")
            }
            "status" -> ToolResult(true, name, if (device.bluetoothOn() == true) "Bluetooth ligado." else "Bluetooth desligado.")
            else -> ToolResult(false, name, "Acao invalida para Bluetooth.", JSONObject().put("tool", name))
        }
    }
}

class WifiTool(private val device: DeviceController) : GliaTool {
    override val name: String = "wifi"
    override val description: String = "Liga ou desliga o Wi-Fi."
    override val inputSchemaJson: String = """{"type":"object","properties":{"action":{"type":"string"}},"required":["tool","action"]}"""

    override fun execute(args: JSONObject): ToolResult {
        val action = args.optString("action").ifBlank { "status" }
        return when (action) {
            "on" -> {
                val ok = device.setWifi(true)
                ToolResult(ok, name, if (ok) "Wi-Fi ligado." else "Nao foi possivel ligar o Wi-Fi agora.")
            }
            "off" -> {
                val ok = device.setWifi(false)
                ToolResult(ok, name, if (ok) "Wi-Fi desligado." else "Nao foi possivel descolar o Wi-Fi agora.")
            }
            "status" -> ToolResult(true, name, if (device.wifiOn() == true) "Wi-Fi ligado." else "Wi-Fi desligado.")
            else -> ToolResult(false, name, "Acao invalida para Wi-Fi.", JSONObject().put("tool", name))
        }
    }
}

class ToolBus(private val device: DeviceController) {
    val tools: List<GliaTool> = listOf(
        HoraTool(),
        DataTool(),
        DiaDaSemanaTool(),
        LanternaTool(device),
        BluetoothTool(device),
        WifiTool(device)
    )

    fun execute(name: String, args: JSONObject): ToolResult? = tools.firstOrNull { it.name == name }?.execute(args)
}

object BuiltInRouter {
    fun route(text: String): ToolCall? {
        val t = text.lowercase().trim()
        if (t.isEmpty()) return null
        fun contains(s: String) = t.contains(s)
        if (contains("meia hora") || contains("hora atual") || contains("que horas")) return ToolCall("hora_atual")
        if (contains("qual a data") || contains("que dia") || contains("hoje e") || contains("qual e a data")) return ToolCall("data_atual")
        if (contains("dia da semana")) {
            val date = extractDate(t) ?: Triple(25, 12, 2026)
            val day = date.first; val month = date.second; val year = date.third
            return ToolCall(
                "dia_da_semana",
                JSONObject()
                    .put("day", day)
                    .put("month", month)
                    .put("year", year)
            )
        }
        if (contains("lanterna")) return ToolCall("lanterna", JSONObject().put("action", actionWord(t, "ligar", "desligar", "toggle")))
        if (contains("bluetooth")) return ToolCall("bluetooth", JSONObject().put("action", actionWord(t, "ligar", "desligar", "toggle", "status")))
        if (contains("wi-fi") || contains("wifi")) return ToolCall("wifi", JSONObject().put("action", actionWord(t, "ligar", "desligar", "toggle", "status")))
        return null
    }

    private fun actionWord(t: String, on: String, off: String, def: String, status: String = def): String = when {
        t.contains(status) -> status
        t.contains(on) -> "on"
        t.contains(off) -> "off"
        else -> def
    }

    private fun extractDate(t: String): Triple<Int, Int, Int>? {
        Regex("(\\d{1,2})/(\\d{1,2})/(\\d{2,4})").find(t)?.let { m ->
            return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it })
        }
        Regex("(\\d{1,2}) de (\\d{1,2}) de (\\d{2,4})").find(t)?.let { m ->
            return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it })
        }
        Regex("(\\d{1,2}) de ([a-zA-Z]+) de (\\d{2,4})").find(t)?.let { m ->
            val month = monthFromName(m.groupValues[2]) ?: return@let
            return Triple(m.groupValues[1].toInt(), month, m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it })
        }
        return null
    }

    private fun monthFromName(name: String): Int? = when (name.lowercase()) {
        "janeiro" -> 1; "fevereiro" -> 2; "marco" -> 3; "abril" -> 4; "maio" -> 5; "junho" -> 6
        "julho" -> 7; "agosto" -> 8; "setembro" -> 9; "outubro" -> 10; "novembro" -> 11; "dezembro" -> 12
        else -> java.time.Month.valueOf(name.uppercase()).value
    }
}
