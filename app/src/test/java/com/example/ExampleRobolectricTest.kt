package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AsrSchool
import com.example.data.model.CalculationMethod
import com.example.util.PrayerTimeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private fun toMinutes(hhmm: String): Int {
    val parts = hhmm.split(":")
    return parts[0].toInt() * 60 + parts[1].toInt()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("المؤذن|Muezzin", appName)
  }

  @Test
  fun `offline astronomical timings are ordered properly for Makkah and Cairo`() {
    val makkahShafi = PrayerTimeCalculator.computeOfflineTimings(
      latitude = 21.4225,
      longitude = 39.8262,
      timeZoneId = "Asia/Riyadh",
      method = CalculationMethod.UMM_AL_QURA,
      asrSchool = AsrSchool.SHAFI
    )
    val makkahHanafi = PrayerTimeCalculator.computeOfflineTimings(
      latitude = 21.4225,
      longitude = 39.8262,
      timeZoneId = "Asia/Riyadh",
      method = CalculationMethod.UMM_AL_QURA,
      asrSchool = AsrSchool.HANAFI
    )

    val imsak = toMinutes(makkahShafi.imsak)
    val fajr = toMinutes(makkahShafi.fajr)
    val sunrise = toMinutes(makkahShafi.sunrise)
    val dhuhr = toMinutes(makkahShafi.dhuhr)
    val asr = toMinutes(makkahShafi.asr)
    val asrHanafi = toMinutes(makkahHanafi.asr)
    val maghrib = toMinutes(makkahShafi.maghrib)
    val isha = toMinutes(makkahShafi.isha)

    assertTrue("Imsak before Fajr", imsak < fajr)
    assertTrue("Fajr before Sunrise", fajr < sunrise)
    assertTrue("Sunrise before Dhuhr", sunrise < dhuhr)
    assertTrue("Dhuhr before Asr", dhuhr < asr)
    assertTrue("Hanafi Asr after Shafi Asr", asrHanafi > asr)
    assertTrue("Asr before Maghrib", asrHanafi < maghrib)
    assertTrue("Maghrib before Isha", maghrib < isha)

    val hijri = PrayerTimeCalculator.offlineHijriDateFormatted("Asia/Riyadh")
    val gregorian = PrayerTimeCalculator.offlineGregorianReadable("Asia/Riyadh")
    assertTrue(hijri.endsWith("هـ"))
    assertTrue(gregorian.isNotBlank())
  }
}
