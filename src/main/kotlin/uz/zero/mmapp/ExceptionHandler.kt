package uz.zero.mmapp

import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

import org.springframework.security.core.AuthenticationException

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
                val message = try {
                    messageSource.getMessage(ErrorCode.BAD_CREDENTIALS.toString(), null, LocaleContextHolder.getLocale())
                } catch (e: Exception) {
                    "Login yoki parol noto'g'ri"
                }
                ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(BaseMessage(ErrorCode.BAD_CREDENTIALS.code, message))
            }

            is DataIntegrityViolationException -> {
                ResponseEntity
                    .badRequest()
                    .body(BaseMessage(103, "Ma'lumotlar bazasida ziddiyat: bu ma'lumot allaqachon mavjud."))
            }

            is MethodArgumentNotValidException -> {
                val errors =
                    exception.bindingResult?.fieldErrors?.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
                ResponseEntity
                    .badRequest()
                    .body(BaseMessage(400, "Validatsiya xatosi: $errors"))
            }

            else -> {
                exception.printStackTrace()
                ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseMessage(100, "Iltimos support bilan bog'laning. Xatolik: ${exception.message}"))
            }
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

class OldPasswordIncorrectException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.OLD_PASSWORD_INCORRECT
}

class FutureDateNotAllowedException(message: String? = null) : MyException(message) {
    override fun errorType() = ErrorCode.FUTURE_DATE_NOT_ALLOWED
}