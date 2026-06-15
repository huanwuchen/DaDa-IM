package com.dada.domain.common

/**
 * 领域层统一的结果类型。替代 [com.dada.core.network.model.ApiResult] 在 UseCase 层的使用。
 *
 * UseCase 不再返回 Boolean / 抛网络异常，而是返回 [DomainResult.Success] 或 [DomainResult.Failure]。
 * 上层 ViewModel 据此构建 UiState，错误信息走 DomainError 而非 raw String。
 */
sealed interface DomainResult<out T> {
    data class Success<T>(val data: T) : DomainResult<T>
    data class Failure(val error: DomainError) : DomainResult<Nothing>
}

sealed interface DomainError {
    object Network : DomainError
    object Unauthorized : DomainError
    object NotFound : DomainError
    data class Server(val code: Int, val message: String? = null) : DomainError
    data class Validation(val field: String, val message: String) : DomainError
    data class Unknown(val cause: Throwable? = null) : DomainError
}

inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(data))
    is DomainResult.Failure -> this
}

inline fun <T> DomainResult<T>.onSuccess(block: (T) -> Unit): DomainResult<T> {
    if (this is DomainResult.Success) block(data)
    return this
}

inline fun <T> DomainResult<T>.onFailure(block: (DomainError) -> Unit): DomainResult<T> {
    if (this is DomainResult.Failure) block(error)
    return this
}
