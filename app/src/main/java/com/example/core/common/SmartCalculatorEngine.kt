package com.example.core.common

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Safe decimal expression evaluator for the reusable SKYPLUS MONEY calculator.
 * Supports +, -, ×, ÷, parentheses and postfix percentage. No eval/reflection.
 */
object SmartCalculatorEngine {
  fun evaluate(expression: String, scale: Int = 2): BigDecimal {
    require(scale in 0..8) { "Unsupported decimal precision" }
    require(expression.isNotBlank()) { "Enter a calculation" }
    val parser = Parser(expression)
    val value = parser.parseExpression()
    parser.skipWhitespace()
    require(parser.isAtEnd()) { "Check the expression" }
    return value.setScale(scale, RoundingMode.HALF_UP)
  }

  private class Parser(private val source: String) {
    private var index = 0
    private val context = MathContext.DECIMAL128

    fun isAtEnd(): Boolean = index >= source.length
    fun skipWhitespace() { while (!isAtEnd() && source[index].isWhitespace()) index++ }

    fun parseExpression(): BigDecimal {
      var value = parseTerm()
      while (true) {
        skipWhitespace()
        value = when {
          consume('+') -> value.add(parseTerm(), context)
          consume('-') -> value.subtract(parseTerm(), context)
          else -> return value
        }
      }
    }

    private fun parseTerm(): BigDecimal {
      var value = parseUnary()
      while (true) {
        skipWhitespace()
        value = when {
          consume('*') || consume('×') -> value.multiply(parseUnary(), context)
          consume('/') || consume('÷') -> {
            val divisor = parseUnary()
            require(divisor.compareTo(BigDecimal.ZERO) != 0) { "Cannot divide by zero" }
            value.divide(divisor, context)
          }
          else -> return value
        }
      }
    }

    private fun parseUnary(): BigDecimal {
      skipWhitespace()
      return when {
        consume('+') -> parseUnary()
        consume('-') -> parseUnary().negate(context)
        else -> parsePostfix()
      }
    }

    private fun parsePostfix(): BigDecimal {
      var value = parsePrimary()
      skipWhitespace()
      while (consume('%')) {
        value = value.divide(BigDecimal("100"), context)
        skipWhitespace()
      }
      return value
    }

    private fun parsePrimary(): BigDecimal {
      skipWhitespace()
      if (consume('(')) {
        val value = parseExpression()
        skipWhitespace()
        require(consume(')')) { "Missing closing bracket" }
        return value
      }
      val start = index
      var decimalSeen = false
      while (!isAtEnd()) {
        val ch = source[index]
        if (ch.isDigit()) {
          index++
        } else if (ch == '.' && !decimalSeen) {
          decimalSeen = true
          index++
        } else {
          break
        }
      }
      require(index > start && source.substring(start, index) != ".") { "Enter a valid number" }
      return source.substring(start, index).toBigDecimalOrNull()
        ?: throw IllegalArgumentException("Enter a valid number")
    }

    private fun consume(char: Char): Boolean {
      if (!isAtEnd() && source[index] == char) {
        index++
        return true
      }
      return false
    }
  }
}
