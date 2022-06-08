import java.io.OutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SelfReference<T>(initializer: SelfReference<T>.() -> T)  {
    private val self: T by lazy {
        inner ?: throw IllegalStateException("Do not use the self reference until the object is initialized.")
    }

    private val inner = initializer()
    operator fun invoke(): T = self
}

fun <T : Any> selfReferencing(initializer: SelfReference<T>.() -> T): T = SelfReference(initializer)()

@Suppress("UNUSED")
fun debugLog(vararg arguments: Any?) = println("[${
        ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
    }, ${
        Throwable().stackTrace[1].let { "${it.className.substringAfterLast('.')}#${it.methodName}@L${it.lineNumber}" }
    }] ${
        arguments.joinToString(" | ") { it.toString() }
    }"
)

class MultiOutputStream(private vararg val streams: OutputStream) : OutputStream() {
    override fun close() = streams.forEach(OutputStream::close)
    override fun flush() = streams.forEach(OutputStream::flush)

    override fun write(b: Int) = streams.forEach {
        it.write(b)
    }

    override fun write(b: ByteArray) = streams.forEach {
        it.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) = streams.forEach {
        it.write(b, off, len)
    }
}