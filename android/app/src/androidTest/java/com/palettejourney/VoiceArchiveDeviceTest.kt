package com.palettejourney

import android.media.MediaPlayer
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic PCM only: does not open a microphone or retain a person's voice. */
@RunWith(AndroidJUnit4::class)
class VoiceArchiveDeviceTest {
    @Test fun storedOriginalWavCanBeDecodedAfterReopeningArchive() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,"archive-device-test-${java.util.UUID.randomUUID()}")
        assertTrue(root.mkdir())
        val source=File(root,"synthetic.wav")
        var savedId:String?=null
        val archive=VoiceArchive(File(root,"saved"))
        try {
            WavFile(source).use { it.append(ByteArray(32000),32000) }
            val saved=archive.save(listOf(VoiceArchive.Entry("시험 질문","가상 답변",false,source)))
            savedId=saved.id
            val reopened=VoiceArchive(File(root,"saved")).load(saved.id)
            val player=MediaPlayer()
            try {
                player.setDataSource(reopened.entries[0].audio.absolutePath)
                player.prepare()
                assertTrue(player.duration in 990..1010)
            } finally { player.release() }
            assertEquals("시험 질문",reopened.entries[0].question)
            assertTrue(archive.delete(saved.id));savedId=null
            assertEquals(0,archive.list().size)
        } finally {
            savedId?.let { archive.delete(it) }
            source.delete();File(root,"saved").delete();root.delete()
        }
    }
}
