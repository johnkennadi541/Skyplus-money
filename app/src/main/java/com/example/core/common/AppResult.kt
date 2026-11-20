package com.example.core.common

/**
 * Typed domain/data operation wrapper avoiding unchecked exceptions across Clean Architecture layers.
 */
sealed interface AppResult<out T> {
  data class Success<T>(val data: T) : AppResult<T>

  data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}
