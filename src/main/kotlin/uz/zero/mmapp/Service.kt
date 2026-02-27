package uz.zero.mmapp

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

interface ExpenseService{
    fun createExpense(request: ExpenseRequest): Expenses
    fun getAllExpenses(): List<Expenses>
    fun getMonthlyStats(year: Int, month: Int): MonthlyStatsResponse
    fun exportToExcel(year: Int, month: Int): ByteArray
    fun getStatsByRange(startDate: LocalDate, endDate: LocalDate): RangeStatsResponse
    fun exportToExcelByRange(startDate: LocalDate, endDate: LocalDate): ByteArray
    fun deleteExpense(id: Long): BaseMessage
}
@Service
class ExpenseServiceImpl(
    private val expensesRepository: ExpensesRepository,
    private val categoryRepository: CategoryRepository
): ExpenseService {
    @Transactional
    override fun createExpense(request: ExpenseRequest): Expenses {
        val category = categoryRepository.findByIdAndDeletedFalse(request.categoryId)
            ?: throw CategoryNotFoundException()
        
        val expense = Expenses(
            title = request.title,
            category = category,
            description = request.description,
            amount = request.amount,
            date = request.date
        )
        return expensesRepository.save(expense)
    }

    private fun getCurrentUserId(): Long {
        val principal = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication.principal
        return if (principal is UserPrincipal) {
            principal.id
        } else {
            throw UnauthorizedException()
        }
    }

    override fun getAllExpenses(): List<Expenses> = expensesRepository.findAllByCreatedByAndDeletedFalse(getCurrentUserId())

    override fun getMonthlyStats(year: Int, month: Int): MonthlyStatsResponse {
        val now = LocalDate.now()
        val requestedDate = LocalDate.of(year, month, 1)
        
        if (requestedDate.isAfter(now.with(TemporalAdjusters.lastDayOfMonth()))) {
            throw FutureDateNotAllowedException()
        }

        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.with(TemporalAdjusters.lastDayOfMonth())
        
        val prevMonthStartDate = startDate.minusMonths(1)
        val prevMonthEndDate = prevMonthStartDate.with(TemporalAdjusters.lastDayOfMonth())

        val userId = getCurrentUserId()
        val currentMonthTotal = expensesRepository.sumAmountByDateBetween(startDate, endDate, userId) ?: 0.0
        val previousMonthTotal = expensesRepository.sumAmountByDateBetween(prevMonthStartDate, prevMonthEndDate, userId) ?: 0.0
        
        val difference = currentMonthTotal - previousMonthTotal
        val percentageChange = if (previousMonthTotal != 0.0) (difference / previousMonthTotal) * 100 else 0.0

        val categoryStats = expensesRepository.findCategoryStatistics(startDate, endDate, userId).map {
            CategoryStatDTO(it.categoryName, it.totalAmount)
        }

        return MonthlyStatsResponse(
            month = startDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            totalAmount = currentMonthTotal,
            previousMonthAmount = previousMonthTotal,
            difference = difference,
            percentageChange = percentageChange,
            categoryStats = categoryStats
        )
    }

    override fun exportToExcel(year: Int, month: Int): ByteArray {
        val stats = getMonthlyStats(year, month)
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Monthly Expenses")

        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("Category")
        headerRow.createCell(1).setCellValue("Amount")

        var rowIdx = 1
        stats.categoryStats.forEach {
            val row = sheet.createRow(rowIdx++)
            row.createCell(0).setCellValue(it.categoryName)
            row.createCell(1).setCellValue(it.amount)
        }

        val summaryStartRow = rowIdx + 1
        val summaryRow1 = sheet.createRow(summaryStartRow)
        summaryRow1.createCell(0).setCellValue("Total for ${stats.month}")
        summaryRow1.createCell(1).setCellValue(stats.totalAmount)

        val summaryRow2 = sheet.createRow(summaryStartRow + 1)
        summaryRow2.createCell(0).setCellValue("Previous Month")
        summaryRow2.createCell(1).setCellValue(stats.previousMonthAmount)

        val summaryRow3 = sheet.createRow(summaryStartRow + 2)
        summaryRow3.createCell(0).setCellValue("Difference")
        summaryRow3.createCell(1).setCellValue(stats.difference)

        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        workbook.close()
        return outputStream.toByteArray()
    }

    override fun getStatsByRange(startDate: LocalDate, endDate: LocalDate): RangeStatsResponse {
        val now = LocalDate.now()
        if (endDate.isAfter(now)) {
            throw FutureDateNotAllowedException()
        }
        if (startDate.isAfter(endDate)) {
            throw InvalidRequestException("Boshlanish sanasi tugashidan keyin bo'lishi mumkin emas")
        }

        val userId = getCurrentUserId()
        val totalAmount = expensesRepository.sumAmountByDateBetween(startDate, endDate, userId) ?: 0.0
        val categoryStats = expensesRepository.findCategoryStatistics(startDate, endDate, userId).map {
            CategoryStatDTO(it.categoryName, it.totalAmount)
        }
        val expenses = expensesRepository.findAllByDateBetween(startDate, endDate, userId)

        return RangeStatsResponse(
            startDate = startDate,
            endDate = endDate,
            totalAmount = totalAmount,
            categoryStats = categoryStats,
            expenses = expenses
        )
    }

    override fun exportToExcelByRange(startDate: LocalDate, endDate: LocalDate): ByteArray {
        val stats = getStatsByRange(startDate, endDate)
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Expenses Range Report")

        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("Date")
        headerRow.createCell(1).setCellValue("Category")
        headerRow.createCell(2).setCellValue("Title")
        headerRow.createCell(3).setCellValue("Amount")

        var rowIdx = 1
        stats.expenses.forEach {
            val row = sheet.createRow(rowIdx++)
            row.createCell(0).setCellValue(it.date.toString())
            row.createCell(1).setCellValue(it.category?.name ?: "N/A")
            row.createCell(2).setCellValue(it.title)
            row.createCell(3).setCellValue(it.amount)
        }

        val summaryStartRow = rowIdx + 1
        val summaryHeader = sheet.createRow(summaryStartRow)
        summaryHeader.createCell(0).setCellValue("Summary By Category")
        
        rowIdx = summaryStartRow + 1
        stats.categoryStats.forEach {
            val row = sheet.createRow(rowIdx++)
            row.createCell(0).setCellValue(it.categoryName)
            row.createCell(3).setCellValue(it.amount)
        }

        val totalRow = sheet.createRow(rowIdx + 1)
        totalRow.createCell(0).setCellValue("TOTAL AMOUNT")
        totalRow.createCell(3).setCellValue(stats.totalAmount)

        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        workbook.close()
        return outputStream.toByteArray()
    }

    override fun deleteExpense(id: Long): BaseMessage {
        val expense = expensesRepository.findByIdAndDeletedFalse(id)
            ?: throw InvalidRequestException("Xarajat topilmadi")

        if (expense.createdBy != getCurrentUserId()) {
            throw ForbiddenException("Siz faqat o'zingiz yaratgan xarajatlarni o'chirishingiz mumkin")
        }

        expensesRepository.trash(id)
        return BaseMessage(200, "Xarajat muvaffaqiyatli o'chirildi")
    }
}
interface CategoryService{
    fun createCategory(request: CategoryRequest): Category
    fun getAllCategories(): List<Category>
    fun deleteCategory(id: Long): BaseMessage
}
@Service
class CategoryServiceImpl(private val categoryRepository: CategoryRepository): CategoryService {
    override fun createCategory(request: CategoryRequest): Category {
        val category = Category(name = request.name)
        return categoryRepository.save(category)
    }

    override fun getAllCategories(): List<Category> = categoryRepository.findAllNotDeleted()

    override fun deleteCategory(id: Long): BaseMessage {
        categoryRepository.trash(id)
            ?: throw CategoryNotFoundException()
        return BaseMessage(200, "Kategoriya muvaffaqiyatli o'chirildi")
    }
}

interface AuthService {
    fun register(request: RegisterRequest): UserResponse
    fun login(request: AuthRequest): AuthResponse
    fun changePassword(request: ChangePasswordRequest): BaseMessage
}

@Service
@Transactional
class AuthServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: org.springframework.security.crypto.password.PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: org.springframework.security.authentication.AuthenticationManager
) : AuthService {

    override fun register(request: RegisterRequest): UserResponse {
        if (userRepository.findByUsername(request.username) != null) {
            throw UserAlreadyExistsException()
        }
        val user = User(
            username = request.username,
            password = passwordEncoder.encode(request.password),
            fullName = request.fullName
        )
        val savedUser = userRepository.save(user)
        return UserResponse(
            id = savedUser.id,
            username = savedUser.username,
            fullName = savedUser.fullName,
            role = savedUser.role
        )
    }

    override fun login(request: AuthRequest): AuthResponse {
        authenticationManager.authenticate(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken(request.username, request.password)
        )
        val user = userRepository.findByUsername(request.username)
            ?: throw UserNotFoundException()
        
        val token = jwtService.generateToken(user.username, user.role.name)
        return AuthResponse(token, user.username, user.role.name)
    }

    override fun changePassword(request: ChangePasswordRequest): BaseMessage {
        val username = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username) ?: throw UserNotFoundException()

        if (!passwordEncoder.matches(request.oldPassword, user.password)) {
            throw OldPasswordIncorrectException()
        }

        user.password = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)

        return BaseMessage(200, "Parol muvaffaqiyatli o'zgartirildi")
    }
}
