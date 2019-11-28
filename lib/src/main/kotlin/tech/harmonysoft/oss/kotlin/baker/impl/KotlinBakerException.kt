package tech.harmonysoft.oss.kotlin.baker.impl

class KotlinBakerException @JvmOverloads constructor(
        message: String? = null,
        cause: Throwable? = null
) : RuntimeException(message, cause)