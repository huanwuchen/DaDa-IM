package com.dada.core.network.model

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int = -1, val message: String) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}



