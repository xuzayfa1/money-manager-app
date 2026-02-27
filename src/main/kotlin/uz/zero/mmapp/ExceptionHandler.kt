package uz.zero.mmapp

import jakarta.validation.ConstraintViolationException
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

import org.springframework.security.core.AuthenticationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException

@RestControllerAdvice
class GlobalExceptionHandler(
    private val messageSource: MessageSource,
) {
    @ExceptionHandler(Exception::class)
    fun handleExceptions(exception: Exception): ResponseEntity<Any> {
        return when (exception) {

            is MyException -> {
                ResponseEntity
                    .badRequest()
                    .body(exception.getErrorMessage(messageSource))
            }

            is AuthenticationException -> {
                val message = getLocalizedMessage(ErrorCode.BAD_CREDENTIALS.toString()) ?: "Login yoki parol noto'g'ri"
                ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(BaseMessage(ErrorCode.BAD_CREDENTIALS.code, message))
            }

            is org.springframework.security.access.AccessDeniedException -> {
                val message = getLocalizedMessage(ErrorCode.FORBIDDEN.toString()) ?: "Sizda bu amalni bajarish uchun ruxsat yo'q"
                ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(BaseMessage(ErrorCode.FORBIDDEN.code, message))
            }

            is DataIntegrityViolationException -> {
                ResponseEntity
                    .badRequest()
                    .body(BaseMessage(103, "Ma'lumotlar bazasida ziddiyat: bu ma'lumot allaqachon mavjud."))
            }

            is MethodArgumentNotValidException -> {
                val errorMessage = exception.bindingResult.fieldErrors.firstOrNull()?.let { error ->
                    getLocalizedMessage(error.defaultMessage ?: "") ?: "${error.field}: ${error.defaultMessage}"
                } ?: "Validatsiya xatosi"
                
                ResponseEntity
                    .badRequest()
                    .body(BaseMessage(ErrorCode.VALIDATION_ERROR.code, errorMessage))
            }

            is ConstraintViolationException -> {
                val errorMessage = exception.constraintViolations.firstOrNull()?.let { violation ->
                    getLocalizedMessage(violation.messageTemplate.trim('{', '}')) ?: violation.message
                } ?: "Validatsiya xatosi"

                ResponseEntity
                    .badRequest()
                    .body(BaseMessage(ErrorCode.VALIDATION_ERROR.code, errorMessage))
            }

            is HttpMessageNotReadableException -> {
                ResponseEntity
                    .badRequest()
                    .body(BaseMessage(ErrorCode.INVALID_REQUEST.code, "So'rov formati noto'g'ri (JSON xatosi)"))
            }

            is MethodArgumentTypeMismatchException -> {
                ResponseEntity
                    .badRequest()
                    .body(BaseMessage(ErrorCode.INVALID_REQUEST.code, "Parametr turi noto'g'ri: ${exception.name}"))
            }

            is MissingServletRequestParameterException -> {
                ResponseEntity
                    .badRequest()
                    .body(BaseMessage(ErrorCode.INVALID_REQUEST.code, "Majburiy parametr yetishmayapti: ${exception.parameterName}"))
            }

            is HttpRequestMethodNotSupportedException -> {
                ResponseEntity
                    .status(HttpStatus.METHOD_NOT_ALLOWED)
                    .body(BaseMessage(405, "Bu servisda ${exception.method} metodi qo'llab-quvvatlanmaydi"))
            }

            else -> {
                exception.printStackTrace()
                ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseMessage(100, "Iltimos support bilan bog'laning. Xatolik: ${exception.message}"))
            }
        }
    }

    private fun getLocalizedMessage(key: String): String? {
        return try {
            messageSource.getMessage(key, null, LocaleContextHolder.getLocale())
        } catch (e: Exception) {
            null
        }
    }
}



sealed class MyException(message: String? = null) : RuntimeException(message) {
    abstract fun errorType(): ErrorCode
    protected open fun getErrorMessageArguments(): Array<Any?>? = null

    fun getErrorMessage(errorMessageSource: MessageSource): BaseMessage {
        val message = try {
            errorMessageSource.getMessage(
                errorType().toString(),
                getErrorMessageArguments(),
                LocaleContextHolder.getLocale()
            )
        } catch (e: Exception) {
            errorType().toString().replace("_", " ")
        }

        return BaseMessage(errorType().code, message)
    }
}

class UserNotFoundException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.USER_NOT_FOUND
}

class UserAlreadyExistsException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.USER_ALREADY_EXISTS
}

class OrganizationNotFoundException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.ORGANIZATION_NOT_FOUND
}

class OrganizationAlreadyExistsException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.ORGANIZATION_ALREADY_EXISTS
}

class EmployeeNotFoundException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.EMPLOYEE_NOT_FOUND
}

class EmployeeAlreadyExistsException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.EMPLOYEE_ALREADY_EXISTS
}

class InvalidRequestException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.INVALID_REQUEST
}

class UnauthorizedException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.UNAUTHORIZED
}

class ForbiddenException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.FORBIDDEN
}

class InactiveUserException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.INACTIVE_USER
}

class InactiveOrganizationException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.INACTIVE_ORGANIZATION
}

class CategoryNotFoundException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.CATEGORY_NOT_FOUND
}

class CategoryAlreadyExistsException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.CATEGORY_ALREADY_EXISTS
}

class OldPasswordIncorrectException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.OLD_PASSWORD_INCORRECT
}

class FutureDateNotAllowedException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.FUTURE_DATE_NOT_ALLOWED
}