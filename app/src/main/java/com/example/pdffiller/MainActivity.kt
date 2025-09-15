package com.example.pdffiller

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@Serializable
data class Profile(
  var familienname: String = "Brini",
  var vorname: String = "Marco",
  var geburtsdatum: String = "14.10.1972",
  var zivilstand: String = "Getrennt",
  var nationalitaet: String = "Tessin / Schweiz",
  var strasse: String = "Muhammad Ibn Abi Sufra 6",
  var plz_ort: String = "12223 Riyadh",
  var mobile: String = "+966541144429",
  var email: String = "mrcbrini@gmail.com",
  var beruf: String = "Senior Director Agri Innovation & R&D",
  var arbeitgeber: String = "SAI Platform",
  var einkommen: String = "CHF 10’000 – 15’000",
  var objekt_adresse: String = "In der Wässeri 4",
  var objekt_plz_ort: String = "8047 Zürich",
  var zimmer: String = "2",
  var stockwerk: String = "2. OG",
  var bezugsdatum: String = "01.10.2025",
  var mietzins: String = "CHF 1’450.–",
  var haustiere: String = "Keine",
  var betreibung: String = "nein"
)

val labelMap = listOf(
  "Liegenschaft" to "objekt_adresse",
  "PLZ/Ort" to "objekt_plz_ort",
  "Anzahl Zimmer" to "zimmer",
  "Stockwerk" to "stockwerk",
  "Gewünschter Bezugstermin" to "bezugsdatum",
  "Max. Mietzins" to "mietzins",
  "Familienname" to "familienname",
  "Vorname" to "vorname",
  "Geburtsdatum" to "geburtsdatum",
  "Zivilstand" to "zivilstand",
  "Heimatort/Nationalität" to "nationalitaet",
  "Beruf" to "beruf",
  "Arbeitgeber" to "arbeitgeber",
  "Strasse" to "strasse",
  "PLZ/Ort" to "plz_ort",
  "Mobile-Nr." to "mobile",
  "E-Mail-Adresse" to "email",
  "Monatseinkommen brutto" to "einkommen",
  "Welche/wie viele Haustiere haben Sie?" to "haustiere"
)

val checkboxMap = mapOf(
  "Garage/Parkplatz?" to "nein",
  "Bestehen Betreibungen?" to "nein",
  "Wie wurde Ihr Interesse geweckt?" to "Internet"
)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { App(this) }
  }
}

@Composable
fun App(activity: ComponentActivity) {
  var profile by remember { mutableStateOf(Profile()) }
  var pdfUri by remember { mutableStateOf<Uri?>(null) }
  var status by remember { mutableStateOf("") }

  val pickJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      val txt = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
      try {
        profile = Json.decodeFromString(Profile.serializer(), txt ?: "")
        status = "json geladen"
      } catch (e: Exception) { status = "json fehler: ${e.message}" }
    }
  }
  val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) { pdfUri = uri; status = "pdf geladen" }
  }

  Scaffold(topBar = { TopAppBar(title = { Text("pdf filler") }) }) { pad ->
    Column(Modifier.padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { pickJson.launch(arrayOf("application/json","text/plain")) }) { Text("json laden") }
        Button(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) { Text("pdf wählen") }
      }
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(value = profile.objekt_adresse, onValueChange = { profile = profile.copy(objekt_adresse = it) }, label = { Text("objekt adresse") })
      OutlinedTextField(value = profile.objekt_plz_ort, onValueChange = { profile = profile.copy(objekt_plz_ort = it) }, label = { Text("objekt plz/ort") })
      OutlinedTextField(value = profile.bezugsdatum, onValueChange = { profile = profile.copy(bezugsdatum = it) }, label = { Text("bezugstermin") })
      OutlinedTextField(value = profile.mietzins, onValueChange = { profile = profile.copy(mietzins = it) }, label = { Text("mietzins") })
      Spacer(Modifier.height(8.dp))
      Button(enabled = pdfUri != null, onClick = {
        status = try {
          val out = fillOverlay(activity, pdfUri!!, profile)
          shareFile(activity, out)
          "fertig. pdf exportiert."
        } catch (e: Exception) { "error: ${e.message}" }
      }) { Text("pdf ausfüllen & speichern") }
      Spacer(Modifier.height(8.dp))
      Text(status)
    }
  }
}

private fun shareFile(activity: ComponentActivity, file: File) {
  val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", file)
  val intent = Intent(Intent.ACTION_SEND).apply {
    type = "application/pdf"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  activity.startActivity(Intent.createChooser(intent, "pdf teilen"))
}

private fun InputStream.copyToFile(dest: File) = use { input ->
  FileOutputStream(dest).use { output -> input.copyTo(output) }
}

private fun fillOverlay(activity: ComponentActivity, uri: Uri, p: Profile): File {
  val tmpIn = File(activity.externalCacheDir, "in.pdf")
  activity.contentResolver.openInputStream(uri)!!.copyToFile(tmpIn)

  val src = PDDocument.load(tmpIn)
  val renderer = PDFRenderer(src)
  val pageCount = src.numberOfPages
  val bitmaps = mutableListOf<Bitmap>()
  for (i in 0 until pageCount) {
    val bmp = renderer.renderImageWithDPI(i, 200f)
    bitmaps += bmp
  }

  val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  val pages = mutableListOf<Map<String, android.graphics.RectF>>()

  for (i in 0 until pageCount) {
    val image = InputImage.fromBitmap(bitmaps[i], 0)
    val result = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
    val map = mutableMapOf<String, android.graphics.RectF>()
    result.textBlocks.forEach { b ->
      b.lines.forEach { l ->
        val t = l.text.trim()
        labelMap.forEach { (label, _) ->
          if (!map.containsKey(label) && t.contains(label, ignoreCase = true) && l.boundingBox != null) {
            val bb = l.boundingBox!!
            map[label] = android.graphics.RectF(bb.left.toFloat(), bb.top.toFloat(), bb.right.toFloat(), bb.bottom.toFloat())
          }
        }
      }
    }
    pages += map
  }

  val outFile = File(activity.externalCacheDir, "form_filled.pdf")
  val pdf = PdfDocument()
  val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f } // ~11pt at 200dpi
  val paintBox = Paint().apply { style = Paint.Style.FILL }

  fun pToMap(pp: Profile) = mapOf(
    "familienname" to pp.familienname,
    "vorname" to pp.vorname,
    "geburtsdatum" to pp.geburtsdatum,
    "zivilstand" to pp.zivilstand,
    "nationalitaet" to pp.nationalitaet,
    "strasse" to pp.strasse,
    "plz_ort" to pp.plz_ort,
    "mobile" to pp.mobile,
    "email" to pp.email,
    "beruf" to pp.beruf,
    "arbeitgeber" to pp.arbeitgeber,
    "einkommen" to pp.einkommen,
    "objekt_adresse" to pp.objekt_adresse,
    "objekt_plz_ort" to pp.objekt_plz_ort,
    "zimmer" to pp.zimmer,
    "stockwerk" to pp.stockwerk,
    "bezugsdatum" to pp.bezugsdatum,
    "mietzins" to pp.mietzins,
    "haustiere" to pp.haustiere,
    "betreibung" to pp.betreibung
  )

  for (i in 0 until pageCount) {
    val pageInfo = PdfDocument.PageInfo.Builder(bitmaps[i].width, bitmaps[i].height, i+1).create()
    val page = pdf.startPage(pageInfo)
    val canvas = page.canvas
    canvas.drawBitmap(bitmaps[i], 0f, 0f, null)

    val map = pages[i]
    val values = pToMap(p)

    fun drawAt(label: String, key: String) {
      val r = map[label] ?: return
      val v = values[key] ?: return
      val x = r.right + 36f
      val y = r.bottom - 6f
      canvas.drawText(v, x, y, paintText)
    }

    labelMap.forEach { (label, key) -> drawAt(label, key) }

    checkboxMap.forEach { (row, _opt) ->
      val r = map[row] ?: return@forEach
      val x = r.right + when (row) {
        "Wie wurde Ihr Interesse geweckt?" -> 200f
        else -> 100f
      }
      val y = r.centerY()
      canvas.drawRect(x, y - 10f, x + 10f, y, paintBox)
    }

    pdf.finishPage(page)
  }
  pdf.writeTo(FileOutputStream(outFile))
  pdf.close()
  src.close()
  return outFile
}
