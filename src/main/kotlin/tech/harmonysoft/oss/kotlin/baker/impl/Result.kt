package tech.harmonysoft.oss.kotlin.baker.impl

@Suppress("UNCHECKED_CAST")
class Result<S, F> private constructor(
        private val _successValue: S?,
        private val _failureValue: F?,
        val success: Boolean
) {

    val successValue: S
        get() {
            if (!success) {
                throw IllegalStateException("Can't return a success value from a failed result")
            }
            return _successValue as S
        }

    val failureValue: F
        get() {
            if (success) {
                throw IllegalStateException("Can't return a failure value from a successful result")
            }
            return _failureValue as F
        }

    override fun toString(): String {
        return if (success) {
            "success: $successValue"
        } else {
            "failure: $failureValue"
        }
    }

    companion object {

        fun <S, F> success(result: S): Result<S, F> {
            return Result(result, null, true)
        }

        fun <S, F> failure(error: F): Result<S, F> {
            return Result(null, error, false)
        }
    }
}