package uz.zero.mmapp

import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/categories")
@PreAuthorize("hasRole('ADMIN')")
class CategoryController(private val categoryService: CategoryService) {

    @PostMapping
    fun create(@Valid @RequestBody request: CategoryRequest) =
        categoryService.createCategory(request)

    @GetMapping
    fun getAllCategories() = categoryService.getAllCategories()

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteCategory(@PathVariable id: Long) = categoryService.deleteCategory(id)
}

@RestController
@RequestMapping("/api/expenses")
class ExpenseController(private val expenseService: ExpenseService) {

    @PostMapping
    fun create(@Valid @RequestBody request: ExpenseRequest) =
        expenseService.createExpense(request)

    @GetMapping
    fun getAll() = expenseService.getAllExpenses()

    @GetMapping("/stats")
    fun getStats(
        @RequestParam year: Int,
        @RequestParam month: Int
    ): MonthlyStatsResponse = expenseService.getMonthlyStats(year, month)

    @GetMapping("/export")
    fun exportExcel(
        @RequestParam year: Int,
        @RequestParam month: Int
    ): ResponseEntity<ByteArray> {
        val data = expenseService.exportToExcel(year, month)
        val filename = "expenses_${year}_${month}.xlsx"
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$filename")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(data)
    }

    @GetMapping("/range-stats")
    fun getStatsByRange(
        @RequestParam from: String, // format: yyyy-MM-dd
        @RequestParam to: String
    ): RangeStatsResponse {
        val startDate = LocalDate.parse(from)
        val endDate = LocalDate.parse(to)
        return expenseService.getStatsByRange(startDate, endDate)
    }

    @GetMapping("/range-export")
    fun exportExcelByRange(
        @RequestParam from: String,
        @RequestParam to: String
    ): ResponseEntity<ByteArray> {
        val startDate = LocalDate.parse(from)
        val endDate = LocalDate.parse(to)
        val data = expenseService.exportToExcelByRange(startDate, endDate)
        val filename = "expenses_range_${from}_to_${to}.xlsx"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$filename")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(data)
    }

    @DeleteMapping("/{id}")
    fun deleteExpense(@PathVariable id: Long) = expenseService.deleteExpense(id)
}

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest) =
        authService.register(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: AuthRequest) =
        authService.login(request)

    @PostMapping("/change-password")
    fun changePassword(@Valid @RequestBody request: ChangePasswordRequest) =
        authService.changePassword(request)
}
