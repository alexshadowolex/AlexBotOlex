class SelfReference<T>(initializer: SelfReference<T>.() -> T)  {
    private val self: T by lazy {
        inner ?: throw IllegalStateException("Do not use the self reference until the object is initialized.")
    }

    private val inner = initializer()
    operator fun invoke(): T = self
}

fun <T : Any> selfReferencing(initializer: SelfReference<T>.() -> T): T = SelfReference(initializer)()