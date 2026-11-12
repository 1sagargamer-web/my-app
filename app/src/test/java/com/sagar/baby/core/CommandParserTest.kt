package com.sagar.baby.core

import com.sagar.baby.core.Command.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    private fun p(s: String) = CommandParser.parse(s)

    @Test fun transliteration() {
        assertEquals("kholo", Translit.norm("खोलो"))
        assertEquals("bhejo", Translit.norm("भेजो"))
        assertEquals("rahul", Translit.norm("राहुल"))
        assertEquals("kitne", Translit.norm("कितने"))
        assertEquals("samay", Translit.norm("समय"))
        assertEquals("foto", Translit.norm("फोटो"))
        assertEquals("selfi", Translit.norm("सेल्फी"))
        assertEquals("maisej", Translit.norm("मैसेज"))
        assertEquals("ko", Translit.norm("को"))
    }

    @Test fun openApps() {
        assertEquals(OpenApp("YouTube"), p("youtube kholo"))
        assertEquals(OpenApp("YouTube"), p("यूट्यूब खोलो"))
        assertEquals(OpenApp("YouTube"), p("open YouTube"))
        assertEquals(OpenApp("instagram"), p("instagram kholo"))
        assertEquals(OpenApp("इंस्टाग्राम"), p("इंस्टाग्राम खोलो"))
        assertEquals(OpenApp("WhatsApp"), p("WhatsApp kholo"))
        assertEquals(OpenApp("WhatsApp"), p("baby WhatsApp open karo"))
        assertEquals(OpenApp("Free Fire"), p("Free Fire chalao"))
    }

    @Test fun youtubeSearch() {
        assertEquals(YouTubeSearch("arijit singh ke gaane"), p("youtube pe arijit singh ke gaane chalao"))
        assertEquals(YouTubeSearch("funny videos"), p("YouTube par funny videos dikhao"))
        assertEquals(YouTubeSearch("hindi songs"), p("gaana bajao"))
        assertEquals(YouTubeSearch("kesariya song"), p("kesariya song play karo"))
    }

    @Test fun messages() {
        assertEquals(SendMessage("Rahul", "kal milte hain", Platform.ANY), p("Rahul ko message bhejo ki kal milte hain"))
        assertEquals(SendMessage("Rahul", "hello", Platform.ANY), p("Rahul ko hello bhejo"))
        assertEquals(SendMessage("Mummy", "main aa raha hoon", Platform.WHATSAPP), p("Mummy ko WhatsApp pe bolo ki main aa raha hoon"))
        assertEquals(SendMessage("Rahul", "good morning", Platform.ANY), p("Rahul ko good morning ka message bhejo"))
        assertEquals(SendMessage("rahul", null, Platform.SMS), p("rahul ko sms bhejo"))
        assertEquals(SendMessage("राहुल", "मैं आ रहा हूँ", Platform.ANY), p("राहुल को मैसेज भेजो कि मैं आ रहा हूँ"))
        assertEquals(SendMessage("Rahul", "hi", Platform.WHATSAPP), p("send hi to Rahul on WhatsApp"))
        assertEquals(SendMessage(null, null, Platform.ANY), p("message bhejo"))
        assertEquals(SendMessage("Priya", "good night", Platform.WHATSAPP), p("whatsapp pe Priya ko good night bhej do"))
    }

    @Test fun calls() {
        assertEquals(Call("Papa"), p("Papa ko call karo"))
        assertEquals(Call("mummy"), p("mummy ko phone lagao"))
        assertEquals(Call("Rahul"), p("call Rahul"))
        assertEquals(Call(null), p("call karo"))
        // "kal" (kal milte hain) call nahi hai
        assertTrue(p("Rahul ko message bhejo ki kal milte hain") is SendMessage)
    }

    @Test fun camera() {
        assertEquals(TakePhoto(front = false, autoCapture = true), p("camera kholo aur photo click karo"))
        assertEquals(TakePhoto(front = false, autoCapture = false), p("camera kholo"))
        assertEquals(TakePhoto(front = true, autoCapture = true), p("selfie lo"))
        assertEquals(TakePhoto(front = false, autoCapture = true), p("photo khicho"))
        assertEquals(TakePhoto(front = false, autoCapture = true), p("फोटो खींचो"))
        assertEquals(TakePhoto(front = true, autoCapture = true), p("front camera se photo lo"))
        assertEquals(OpenApp("gallery"), p("photos kholo"))
    }

    @Test fun torchVolume() {
        assertEquals(Torch(true), p("torch jalao"))
        assertEquals(Torch(false), p("torch band karo"))
        assertEquals(Torch(true), p("flashlight on"))
        assertEquals(Torch(true), p("टॉर्च जलाओ"))
        assertEquals(Volume(VolumeAction.UP), p("awaaz badhao"))
        assertEquals(Volume(VolumeAction.DOWN), p("volume kam karo"))
        assertEquals(Volume(VolumeAction.MAX), p("volume full karo"))
        assertEquals(Volume(VolumeAction.MUTE), p("mute karo"))
    }

    @Test fun alarmTimer() {
        assertEquals(Alarm(7, 0, true), p("subah 7 baje ka alarm lagao"))
        assertEquals(Alarm(18, 30, true), p("shaam saade 6 baje alarm laga do"))
        assertEquals(Alarm(7, 30, false), p("7:30 ka alarm lagao"))
        assertEquals(Alarm(5, 0, false), p("alarm laga do 5 baje"))
        assertEquals(Alarm(22, 0, true), p("raat 10 baje alarm"))
        assertEquals(Timer(300), p("5 minute ka timer lagao"))
        assertEquals(Timer(90), p("1 minute 30 second ka timer"))
    }

    @Test fun infoAndOthers() {
        assertEquals(TellTime, p("time kya hua"))
        assertEquals(TellTime, p("kitne baje hain"))
        assertEquals(TellDate, p("aaj konsa din hai"))
        assertEquals(TellDate, p("aaj ki date batao"))
        assertEquals(Battery, p("battery kitni hai"))
        assertEquals(OpenSettings(SettingsPage.WIFI), p("wifi on karo"))
        assertEquals(OpenSettings(SettingsPage.BLUETOOTH), p("bluetooth kholo"))
        assertEquals(Navigate("Delhi"), p("Delhi ka rasta dikhao"))
        assertEquals(WebSearch("cricket score"), p("google pe cricket score search karo"))
        assertEquals(WebSearch("Taj Mahal"), p("Taj Mahal ke bare me batao"))
        assertEquals(GoHome, p("home screen pe jao"))
        assertEquals(Joke, p("ek joke sunao"))
        assertEquals(WhoAreYou, p("tumhara naam kya hai"))
        assertEquals(Greeting, p("kaise ho"))
        assertEquals(Help, p("tum kya kya kar sakti ho"))
        assertEquals(Stop, p("chup"))
    }

    @Test fun yesNo() {
        assertTrue(CommandParser.isYes("haan"))
        assertTrue(CommandParser.isYes("haan bhejo"))
        assertTrue(CommandParser.isNo("nahi"))
        assertTrue(CommandParser.isNo("rehne do"))
    }

    @Test fun fuzzy() {
        assertTrue(Fuzzy.similarity(Translit.norm("राहुल"), Translit.norm("Rahul")) >= 0.9)
        assertTrue(Fuzzy.similarity(Translit.norm("यूट्यूब"), Translit.norm("YouTube")) >= 0.75)
    }
}
