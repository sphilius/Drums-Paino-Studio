package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {
    private const val TAG = "AudioDecoder"

    /**
     * Decodes an audio file at the given Uri into a raw FloatArray of mono PCM samples
     */
    fun decodeToPcm(context: Context, uri: Uri): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: return null

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }

            if (trackIndex == -1 || format == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val pcmDataBytes = mutableListOf<ByteArray>()
            var totalBytes = 0

            val info = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isInputEOS = true
                        } else {
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && info.size > 0) {
                        val bytes = ByteArray(info.size)
                        outputBuffer.position(info.offset)
                        outputBuffer.get(bytes)
                        pcmDataBytes.add(bytes)
                        totalBytes += info.size
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Output format changed (can verify sample rate / channels here)
                }
            }

            // Combine bytes and convert to floats (Assuming 16-bit PCM output from decoder)
            val byteBuffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (arr in pcmDataBytes) {
                byteBuffer.put(arr)
            }
            byteBuffer.flip()

            val totalShorts = totalBytes / 2
            val rawFloats = FloatArray(totalShorts)
            val shortBuffer = byteBuffer.asShortBuffer()
            for (i in 0 until totalShorts) {
                rawFloats[i] = shortBuffer.get(i) / 32768f
            }

            // If stereo, convert to mono by averaging channels to make it easier for drum triggering
            val monoFloats = if (channelCount == 2) {
                val monoSize = totalShorts / 2
                val monoArr = FloatArray(monoSize)
                for (i in 0 until monoSize) {
                    monoArr[i] = (rawFloats[i * 2] + rawFloats[i * 2 + 1]) / 2f
                }
                monoArr
            } else {
                rawFloats
            }

            return Pair(monoFloats, sampleRate)

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding custom sample file: ${e.message}", e)
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (ignored: Exception) {}
            try {
                extractor.release()
            } catch (ignored: Exception) {}
        }
    }
}
