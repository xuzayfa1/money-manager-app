package uz.zero.mmapp

import jakarta.validation.constraints.PastOrPresent
import java.time.LocalDate

data class ExpenseRequest(
    val title: String,
    val categoryId: Long,
    val description: String?,
    val amount: Double,
    @field:PastOrPresent(message = "DATE_CANNOT_BE_FUTURE")
    val date: LocalDate
)

data class CategoryRequest(
    val name: String
)

data class CategoryResponse(
    val id: Long?,
    val name: String
)

data class ExpenseResponse(
    val id: Long?,
    val title: String,
    val amount: Double,
    val date: LocalDate,
    val description: String?,
    val category: CategoryResponse?
)

data class MonthlyStatsResponse(
    val month: String,
    val totalAmount: Double,
    val previousMonthAmount: Double,
    val difference: Double,
    val percentageChange: Double,
    val categoryStats: List<CategoryStatDTO>
)

data class CategoryStatDTO(
    val categoryName: String,
    val amount: Double
)

data class BaseMessage(
    val code: Int,
    val message: String?
)

data class AuthRequest(
    val username: String,
    val password:  String
)

data class AuthResponse(
    val token: String,
    val username: String,
    val role: String
)

data class RegisterRequest(
    val username: String,
    val password:  String,
    val fullName: String?
)

data class UserResponse(
    val id: Long?,
    val username: String,
    val fullName: String?,
    val role: UserRole
)

data class ChangePasswordRequest(
    val oldPassword:  String,
    val newPassword:  String
)

data class RangeStatsResponse(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalAmount: Double,
    val categoryStats: List<CategoryStatDTO>,
    val expenses: List<ExpenseResponse>
)
