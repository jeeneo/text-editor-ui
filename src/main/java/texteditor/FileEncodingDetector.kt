package texteditor

import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class FileEncoding(
    val charset: Charset,
    val byteOrderMarkLength: Int,
) {
    val displayName: String
        get() = charset.name()
}

object FileEncodingDetector {
    const val SAMPLE_SIZE = 64 * 1024
    private const val MIN_UTF16_PAIRS = 4
    private const val MAX_UTF16_PAIRS = 2048
    private val UTF_8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF_16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF_16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    fun detect(sample: ByteArray, fallback: Charset = StandardCharsets.UTF_8): FileEncoding {
        detectByteOrderMark(sample)?.let { return it }
        detectUtf16WithoutBom(sample)?.let { return it }
        if (isUtf8(sample)) return FileEncoding(StandardCharsets.UTF_8, 0)
        return FileEncoding(fallback, 0)
    }

    fun detect(
        input: BufferedInputStream,
        fallback: Charset = StandardCharsets.UTF_8,
    ): FileEncoding = detect(readSample(input), fallback)

    private fun readSample(input: BufferedInputStream): ByteArray {
        input.mark(SAMPLE_SIZE + 1)
        try {
            val sample = ByteArray(SAMPLE_SIZE)
            var length = 0
            while (length < SAMPLE_SIZE) {
                val read = input.read(sample, length, SAMPLE_SIZE - length)
                if (read < 0) break
                length += read
            }
            return if (length == SAMPLE_SIZE) sample else sample.copyOf(length)
        } finally {
            input.reset()
        }
    }

    private fun detectByteOrderMark(sample: ByteArray): FileEncoding? = when {
        sample.startsWith(UTF_8_BOM) -> FileEncoding(StandardCharsets.UTF_8, UTF_8_BOM.size)

        sample.startsWith(UTF_16_LE_BOM) -> FileEncoding(
            StandardCharsets.UTF_16LE,
            UTF_16_LE_BOM.size
        )

        sample.startsWith(UTF_16_BE_BOM) -> FileEncoding(
            StandardCharsets.UTF_16BE,
            UTF_16_BE_BOM.size
        )

        else -> null
    }

    private fun detectUtf16WithoutBom(sample: ByteArray): FileEncoding? {
        val pairs = sample.size / 2
        if (pairs < MIN_UTF16_PAIRS) return null
        val examined = minOf(pairs, MAX_UTF16_PAIRS)
        var evenZeros = 0
        var oddZeros = 0
        for (pair in 0 until examined) {
            if (sample[pair * 2] == 0.toByte()) evenZeros++
            if (sample[pair * 2 + 1] == 0.toByte()) oddZeros++
        }
        return when {
            oddZeros >= examined * 3 / 4 && evenZeros <= examined / 16 -> FileEncoding(
                StandardCharsets.UTF_16LE,
                0
            )

            evenZeros >= examined * 3 / 4 && oddZeros <= examined / 16 -> FileEncoding(
                StandardCharsets.UTF_16BE,
                0
            )

            else -> null
        }
    }

    private fun isUtf8(sample: ByteArray): Boolean {
        val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(sample)
        val output = CharBuffer.allocate(4096)
        while (true) {
            val result = decoder.decode(input, output, false)
            when {
                result.isUnderflow -> return true
                result.isError -> return false
                else -> output.clear()
            }
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (index in prefix.indices) {
            if (this[index] != prefix[index]) return false
        }
        return true
    }
}

fun BufferedInputStream.readerFor(encoding: FileEncoding): InputStreamReader {
    repeat(encoding.byteOrderMarkLength) { read() }
    return InputStreamReader(this, encoding.charset)
}
