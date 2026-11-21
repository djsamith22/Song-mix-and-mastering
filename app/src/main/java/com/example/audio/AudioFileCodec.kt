package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Decodes user-selected MP3/WAV/M4A/AAC/OGG/FLAC files using MediaExtractor + MediaCodec
 * (plus fast RIFF/WAVE fallback) and renders mastered 16-bit PCM WAV files to a SAF Uri.
 */
object AudioFileCodec {

    suspend fun decodeAudioUri(context: Context, uri: Uri): DecodedAudio = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(context, uri) ?: "audio_track"

        // Try direct WAV parser first if header matches RIFF/WAVE PCM or IEEE Float
        tryParseWavFastPath(context, uri, displayName)?.let { return@withContext it }

        // General Android MediaExtractor + MediaCodec decoder for MP3, AAC, M4A, OGG, FLAC, WAV
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            require(audioTrackIndex >= 0 && inputFormat != null) {
                "No supported audio track found in file."
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: ""
            var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2
            var pcmEncoding = if (inputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                inputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else AudioFormat.ENCODING_PCM_16BIT

            val chunks = ArrayList<FloatArray>()
            var totalFloats = 0

            if (mime == "audio/raw") {
                val buf = ByteBuffer.allocate(65536).order(ByteOrder.LITTLE_ENDIAN)
                while (true) {
                    buf.clear()
                    val read = extractor.readSampleData(buf, 0)
                    if (read < 0) break
                    buf.limit(read)
                    val floats = bytesToNormalizedFloats(buf, pcmEncoding)
                    if (floats.isNotEmpty()) {
                        chunks.add(floats)
                        totalFloats += floats.size
                    }
                    extractor.advance()
                }
            } else {
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(inputFormat, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEos = false
                var sawOutputEos = false

                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEos = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFmt = codec.outputFormat
                            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                            if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                        }
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                outBuf.order(ByteOrder.LITTLE_ENDIAN)
                                val floats = bytesToNormalizedFloats(outBuf, pcmEncoding)
                                if (floats.isNotEmpty()) {
                                    chunks.add(floats)
                                    totalFloats += floats.size
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEos = true
                            }
                        }
                    }
                }
            }

            require(totalFloats > 0) { "Decoded audio contains no samples." }

            // Downmix >2 channels to stereo if needed, or keep mono/stereo
            val normalizedChannels = if (channelCount >= 2) 2 else 1
            val combined = if (channelCount == normalizedChannels) {
                FloatArray(totalFloats).also { dst ->
                    var offset = 0
                    for (chunk in chunks) {
                        chunk.copyInto(dst, offset)
                        offset += chunk.size
                    }
                }
            } else {
                // Fold multi-channel (e.g. 5.1) into stereo (L, R)
                val frames = totalFloats / channelCount
                FloatArray(frames * 2).also { dst ->
                    var frameOut = 0
                    var posInChunk = 0
                    for (chunk in chunks) {
                        var i = 0
                        while (i + channelCount <= chunk.size && frameOut < frames) {
                            dst[frameOut * 2] = chunk[i]
                            dst[frameOut * 2 + 1] = chunk[i + 1]
                            frameOut++
                            i += channelCount
                        }
                        posInChunk += chunk.size
                    }
                }
            }

            val totalFrames = combined.size / normalizedChannels
            val durationSec = totalFrames.toDouble() / sampleRate.toDouble()

            DecodedAudio(
                displayName = displayName,
                sampleRate = sampleRate,
                channelCount = normalizedChannels,
                interleavedSamples = combined,
                totalFrames = totalFrames,
                durationSec = durationSec
            )
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Throwable) {
            }
            try {
                extractor.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun bytesToNormalizedFloats(buf: ByteBuffer, pcmEncoding: Int): FloatArray {
        val remaining = buf.remaining()
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val count = remaining / 4
                val out = FloatArray(count)
                val fb = buf.asFloatBuffer()
                fb.get(out)
                out
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                val out = FloatArray(remaining)
                for (i in 0 until remaining) {
                    val unsigned = buf.get().toInt() and 0xFF
                    out[i] = (unsigned - 128) / 128f
                }
                out
            }
            else -> {
                val count = remaining / 2
                val out = FloatArray(count)
                val sb = buf.asShortBuffer()
                for (i in 0 until count) {
                    out[i] = sb.get() / 32768f
                }
                out
            }
        }
    }

    private fun tryParseWavFastPath(
        context: Context,
        uri: Uri,
        displayName: String
    ): DecodedAudio? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(12)
                if (readFully(stream, header) < 12) return null
                val riff = String(header, 0, 4, Charsets.US_ASCII)
                val wave = String(header, 8, 4, Charsets.US_ASCII)
                if (riff != "RIFF" || wave != "WAVE") return null

                var audioFormat = 0
                var channels = 0
                var sampleRate = 0
                var bitsPerSample = 0
                var dataBytes: ByteArray? = null

                val chunkHeader = ByteArray(8)
                while (readFully(stream, chunkHeader) == 8) {
                    val bb = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN)
                    val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                    val size = bb.getInt(4).toLong() and 0xFFFFFFFFL
                    if (id == "fmt " && size >= 16) {
                        val fmtBytes = ByteArray(size.toInt())
                        if (readFully(stream, fmtBytes) < size.toInt()) return null
                        val fb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                        audioFormat = fb.getShort(0).toInt() and 0xFFFF
                        channels = fb.getShort(2).toInt() and 0xFFFF
                        sampleRate = fb.getInt(4)
                        bitsPerSample = fb.getShort(14).toInt() and 0xFFFF
                    } else if (id == "data") {
                        if (size > 200_000_000L) return null // fallback if unusually massive
                        val bytes = ByteArray(size.toInt())
                        val read = readFully(stream, bytes)
                        dataBytes = if (read == bytes.size) bytes else bytes.copyOf(read)
                        break
                    } else {
                        skipFully(stream, size)
                    }
                    if (size % 2L == 1L) skipFully(stream, 1)
                }

                val raw = dataBytes ?: return null
                if (channels <= 0 || sampleRate <= 0) return null
                // Support PCM (1) 16/24/32-bit and IEEE float (3) 32-bit
                if (audioFormat != 1 && audioFormat != 3) return null

                val samples = when {
                    audioFormat == 3 && bitsPerSample == 32 -> {
                        val count = raw.size / 4
                        val out = FloatArray(count)
                        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
                        out
                    }
                    audioFormat == 1 && bitsPerSample == 16 -> {
                        val count = raw.size / 2
                        val out = FloatArray(count)
                        val sb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        for (i in 0 until count) out[i] = sb.get() / 32768f
                        out
                    }
                    audioFormat == 1 && bitsPerSample == 24 -> {
                        val count = raw.size / 3
                        val out = FloatArray(count)
                        var idx = 0
                        for (i in 0 until count) {
                            val b0 = raw[idx].toInt() and 0xFF
                            val b1 = raw[idx + 1].toInt() and 0xFF
                            val b2 = raw[idx + 2].toInt() // signed high byte
                            val v = (b2 shl 16) or (b1 shl 8) or b0
                            out[i] = v / 8388608f
                            idx += 3
                        }
                        out
                    }
                    audioFormat == 1 && bitsPerSample == 32 -> {
                        val count = raw.size / 4
                        val out = FloatArray(count)
                        val ib = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
                        for (i in 0 until count) out[i] = (ib.get().toDouble() / 2147483648.0).toFloat()
                        out
                    }
                    else -> return null
                }

                val outChannels = if (channels >= 2) 2 else 1
                val finalSamples = if (channels == outChannels) {
                    samples
                } else {
                    val frames = samples.size / channels
                    FloatArray(frames * 2).also { dst ->
                        for (f in 0 until frames) {
                            dst[f * 2] = samples[f * channels]
                            dst[f * 2 + 1] = samples[f * channels + 1]
                        }
                    }
                }

                val totalFrames = finalSamples.size / outChannels
                DecodedAudio(
                    displayName = displayName,
                    sampleRate = sampleRate,
                    channelCount = outChannels,
                    interleavedSamples = finalSamples,
                    totalFrames = totalFrames,
                    durationSec = totalFrames.toDouble() / sampleRate.toDouble()
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun readFully(stream: InputStream, dest: ByteArray): Int {
        var total = 0
        while (total < dest.size) {
            val read = stream.read(dest, total, dest.size - total)
            if (read <= 0) break
            total += read
        }
        return total
    }

    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                if (stream.read() == -1) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    suspend fun exportWavToUri(
        context: Context,
        uri: Uri,
        audio: DecodedAudio,
        params: MasteringParams,
        isBypassed: Boolean
    ) = withContext(Dispatchers.IO) {
        val nc = audio.channelCount
        val totalFrames = audio.totalFrames
        val dataByteSize = totalFrames.toLong() * nc.toLong() * 2L
        val riffChunkSize = (36L + dataByteSize).toInt()

        val chain = MasteringDspChain(audio.sampleRate, nc).apply {
            updateParams(params)
        }

        val blockFrames = 4096
        val dry = audio.interleavedSamples
        val wetOut = FloatArray(blockFrames * nc)
        val pcmBytes = ByteArray(blockFrames * nc * 2)
        val pcmBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

        context.contentResolver.openOutputStream(uri)?.use { rawOut ->
            BufferedOutputStream(rawOut, 65536).use { out ->
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt(riffChunkSize)
                header.put("WAVE".toByteArray(Charsets.US_ASCII))
                header.put("fmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16) // PCM subchunk size
                header.putShort(1) // AudioFormat = 1 (PCM)
                header.putShort(nc.toShort())
                header.putInt(audio.sampleRate)
                header.putInt(audio.sampleRate * nc * 2) // ByteRate
                header.putShort((nc * 2).toShort()) // BlockAlign
                header.putShort(16) // BitsPerSample
                header.put("data".toByteArray(Charsets.US_ASCII))
                header.putInt(dataByteSize.toInt())
                out.write(header.array())

                var frameOffset = 0
                while (frameOffset < totalFrames) {
                    val framesNow = min(blockFrames, totalFrames - frameOffset)
                    if (isBypassed) {
                        val floatOffset = frameOffset * nc
                        val count = framesNow * nc
                        dry.copyInto(wetOut, 0, floatOffset, floatOffset + count)
                    } else {
                        chain.processBlock(
                            dryInterleaved = dry,
                            dryOffsetFloats = frameOffset * nc,
                            outInterleaved = wetOut,
                            frames = framesNow,
                            isBypassed = false
                        )
                    }

                    pcmBuffer.clear()
                    val sampleCount = framesNow * nc
                    for (i in 0 until sampleCount) {
                        val s = wetOut[i].coerceIn(-1f, 1f)
                        val pcm = if (s < 0f) (s * 32768f).toInt() else (s * 32767f).toInt()
                        pcmBuffer.putShort(pcm.coerceIn(-32768, 32767).toShort())
                    }
                    out.write(pcmBytes, 0, sampleCount * 2)
                    frameOffset += framesNow
                }
                out.flush()
            }
        } ?: error("Unable to open destination file for WAV export.")
    }

    /**
     * Encodes up to [maxDurationSec] of decoded audio as a compact 16 kHz mono 16-bit WAV
     * (<= ~5.7 MB) suitable for Firebase AI Logic multimodal inlineData under the 20 MB request cap.
     */
    suspend fun encodeCompactMonoWavForAi(
        audio: DecodedAudio,
        maxDurationSec: Double = 180.0
    ): ByteArray = withContext(Dispatchers.IO) {
        val targetSr = 16_000
        val srcSr = audio.sampleRate.coerceAtLeast(8000)
        val srcCh = audio.channelCount.coerceAtLeast(1)
        val maxSrcFrames = min(audio.totalFrames, (maxDurationSec * srcSr).toInt())
        val outFrames = ((maxSrcFrames.toLong() * targetSr) / srcSr).toInt().coerceAtLeast(1)
        val dataSize = outFrames * 2

        val baos = ByteArrayOutputStream(44 + dataSize)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // Mono
        header.putInt(targetSr)
        header.putInt(targetSr * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        baos.write(header.array())

        val samples = audio.interleavedSamples
        val pcmBytes = ByteArray(4096 * 2)
        val pcmBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val ratio = srcSr.toDouble() / targetSr.toDouble()

        var outIdx = 0
        while (outIdx < outFrames) {
            val chunk = min(4096, outFrames - outIdx)
            pcmBuf.clear()
            for (i in 0 until chunk) {
                val srcFrame = ((outIdx + i) * ratio).toInt().coerceIn(0, maxSrcFrames - 1)
                val sampleIdx = srcFrame * srcCh
                val mono = if (srcCh >= 2 && sampleIdx + 1 < samples.size) {
                    0.5f * (samples[sampleIdx] + samples[sampleIdx + 1])
                } else if (sampleIdx < samples.size) {
                    samples[sampleIdx]
                } else 0f
                val clamped = mono.coerceIn(-1f, 1f)
                val s16 = if (clamped < 0f) (clamped * 32768f).toInt() else (clamped * 32767f).toInt()
                pcmBuf.putShort(s16.coerceIn(-32768, 32767).toShort())
            }
            baos.write(pcmBytes, 0, chunk * 2)
            outIdx += chunk
        }
        baos.toByteArray()
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Throwable) {
            null
        }
    }
}
