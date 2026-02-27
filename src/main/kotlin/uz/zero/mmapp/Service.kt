package uz.zero.mmapp

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFFont
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

interface ExpenseService{
    fun createExpense(request: ExpenseRequest): ExpenseResponse
    fun getAllExpenses(): List<ExpenseResponse>
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
    override fun createExpense(request: ExpenseRequest): ExpenseResponse {
        val category = categoryRepository.findByIdAndDeletedFalse(request.categoryId)
            ?: throw CategoryNotFoundException()
        
        val expense = Expenses(
            title = request.title,
            category = category,
            description = request.description,
            amount = request.amount,
            date = request.date
        )
        return expensesRepository.save(expense).toResponse()
    }

    private fun getCurrentUserId(): Long {
        val principal = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication.principal
        return if (principal is UserPrincipal) {
            principal.id
        } else {
            throw UnauthorizedException()
        }
    }

    override fun getAllExpenses(): List<ExpenseResponse> = expensesRepository.findAllByCreatedByAndDeletedFalse(getCurrentUserId()).map { it.toResponse() }

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

        val allCategories = categoryRepository.findAllNotDeleted()
        val dbStats = expensesRepository.findCategoryStatistics(startDate, endDate, userId).associate { it.categoryName to it.totalAmount }
        val categoryStats = allCategories.map {
            val amount = dbStats[it.name] ?: 0.0
            val percentage = if (currentMonthTotal > 0) (amount / currentMonthTotal) * 100 else 0.0
            CategoryStatDTO(it.name, amount, percentage)
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
        
        val headerStyle = createHeaderStyle(workbook)
        val dataStyle = createDataStyle(workbook)
        val currencyStyle = createCurrencyStyle(workbook)
        val summaryStyle = createSummaryStyle(workbook)

        val headerRow = sheet.createRow(0)
        createCell(headerRow, 0, "Category", headerStyle)
        createCell(headerRow, 1, "Amount", headerStyle)
        createCell(headerRow, 2, "Percentage", headerStyle)

        var rowIdx = 1
        stats.categoryStats.forEach {
            val row = sheet.createRow(rowIdx++)
            createCell(row, 0, it.categoryName, dataStyle)
            createCell(row, 1, it.amount, currencyStyle)
            createCell(row, 2, "${String.format("%.1f", it.percentage)}%", dataStyle)
        }

        sheet.autoSizeColumn(2)

        val summaryStartRow = rowIdx + 1
        val summaryRow1 = sheet.createRow(summaryStartRow)
        createCell(summaryRow1, 0, "Total for ${stats.month}", summaryStyle)
        createCell(summaryRow1, 1, stats.totalAmount, currencyStyle)

        val summaryRow2 = sheet.createRow(summaryStartRow + 1)
        createCell(summaryRow2, 0, "Previous Month", summaryStyle)
        createCell(summaryRow2, 1, stats.previousMonthAmount, currencyStyle)

        val summaryRow3 = sheet.createRow(summaryStartRow + 2)
        createCell(summaryRow3, 0, "Difference", summaryStyle)
        createCell(summaryRow3, 1, stats.difference, currencyStyle)

        sheet.autoSizeColumn(0)
        sheet.autoSizeColumn(1)

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
        val allCategories = categoryRepository.findAllNotDeleted()
        val dbStats = expensesRepository.findCategoryStatistics(startDate, endDate, userId).associate { it.categoryName to it.totalAmount }
        val categoryStats = allCategories.map {
            val categoryAmount = dbStats[it.name] ?: 0.0
            val percentage = if (totalAmount > 0) (categoryAmount / totalAmount) * 100 else 0.0
            CategoryStatDTO(it.name, categoryAmount, percentage)
        }
        val expenses = expensesRepository.findAllByDateBetween(startDate, endDate, userId).map { it.toResponse() }

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
        
        val titleStyle = createTitleStyle(workbook)
        val headerStyle = createHeaderStyle(workbook)
        val dataStyle = createDataStyle(workbook)
        val currencyStyle = createCurrencyStyle(workbook)
        val summaryStyle = createSummaryStyle(workbook)
        val totalStyle = createTotalStyle(workbook)

        // Title Row
        val titleRow = sheet.createRow(0)
        createCell(titleRow, 0, "EXPENSES REPORT: ${startDate} to ${endDate}", titleStyle)
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3))

        val headerRow = sheet.createRow(1)
        createCell(headerRow, 0, "Date", headerStyle)
        createCell(headerRow, 1, "Category", headerStyle)
        createCell(headerRow, 2, "Title", headerStyle)
        createCell(headerRow, 3, "Amount", headerStyle)

        var rowIdx = 2
        stats.expenses.forEach {
            val row = sheet.createRow(rowIdx++)
            createCell(row, 0, it.date.toString(), dataStyle)
            createCell(row, 1, it.category?.name ?: "N/A", dataStyle)
            createCell(row, 2, it.title, dataStyle)
            createCell(row, 3, it.amount, currencyStyle)
        }

        rowIdx++
        val summaryHeader = sheet.createRow(rowIdx++)
        createCell(summaryHeader, 0, "Summary By Category", summaryStyle)
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 1))
        createCell(summaryHeader, 2, "Amount", summaryStyle)
        createCell(summaryHeader, 3, "Percentage", summaryStyle)
        
        stats.categoryStats.forEach {
            val row = sheet.createRow(rowIdx++)
            createCell(row, 0, it.categoryName, dataStyle)
            sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 1))
            createCell(row, 2, it.amount, currencyStyle)
            createCell(row, 3, "${String.format("%.1f", it.percentage)}%", dataStyle)
        }

        rowIdx++
        val totalRow = sheet.createRow(rowIdx)
        createCell(totalRow, 0, "TOTAL EXPENDITURE", totalStyle)
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowIdx, rowIdx, 0, 2))
        createCell(totalRow, 3, stats.totalAmount, createCurrencyStyle(workbook, true))

        for (i in 0..3) sheet.autoSizeColumn(i)

        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        workbook.close()
        return outputStream.toByteArray()
    }

    private fun createTitleStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.fontHeightInPoints = 14.toShort()
        style.setFont(font)
        style.alignment = HorizontalAlignment.CENTER
        style.fillForegroundColor = IndexedColors.GREY_50_PERCENT.getIndex()
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        font.color = IndexedColors.WHITE.getIndex()
        return style
    }

    private fun createTotalStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.ORANGE.getIndex()
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        font.color = IndexedColors.WHITE.getIndex()
        setBorders(style)
        return style
    }

    private fun createCurrencyStyle(workbook: Workbook, isBold: Boolean = false): CellStyle {
        val style = workbook.createCellStyle()
        val format = workbook.createDataFormat()
        style.dataFormat = format.getFormat("#,##0.00")
        if (isBold) {
            val font = workbook.createFont()
            font.bold = true
            style.setFont(font)
        }
        setBorders(style)
        return style
    }

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.color = IndexedColors.WHITE.getIndex()
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.CORNFLOWER_BLUE.getIndex()
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        setBorders(style)
        return style
    }

    private fun createDataStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        setBorders(style)
        return style
    }

    private fun createCurrencyStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val format = workbook.createDataFormat()
        style.dataFormat = format.getFormat("#,##0.00")
        setBorders(style)
        return style
    }

    private fun createSummaryStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.getIndex()
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        setBorders(style)
        return style
    }

    private fun setBorders(style: CellStyle) {
        style.borderTop = BorderStyle.THIN
        style.borderBottom = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
    }

    private fun createCell(row: Row, column: Int, value: Any?, style: CellStyle) {
        val cell = row.createCell(column)
        when (value) {
            is String -> cell.setCellValue(value)
            is Double -> cell.setCellValue(value)
            is Int -> cell.setCellValue(value.toDouble())
            else -> cell.setCellValue(value?.toString() ?: "")
        }
        cell.cellStyle = style
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
    fun createCategory(request: CategoryRequest): CategoryResponse
    fun getAllCategories(): List<CategoryResponse>
    fun deleteCategory(id: Long): BaseMessage
}
@Service
class CategoryServiceImpl(private val categoryRepository: CategoryRepository): CategoryService {
    override fun createCategory(request: CategoryRequest): CategoryResponse {
        categoryRepository.findByNameIgnoreCase(request.name)?.let {
            throw CategoryAlreadyExistsException()
        }
        val category = Category(name = request.name)
        return categoryRepository.save(category).toResponse()
    }

    override fun getAllCategories(): List<CategoryResponse> = categoryRepository.findAllNotDeleted().map { it.toResponse() }

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
    fun deleteUser(id: Long): BaseMessage
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

    override fun deleteUser(id: Long): BaseMessage {
        userRepository.trash(id) ?: throw UserNotFoundException()
        return BaseMessage(200, "Foydalanuvchi muvaffaqiyatli o'chirildi")
    }
}

private fun Expenses.toResponse() = ExpenseResponse(
    id = this.id,
    title = this.title,
    amount = this.amount,
    date = this.date,
    description = this.description,
    category = this.category?.toResponse()
)

private fun Category.toResponse() = CategoryResponse(
    id = this.id,
    name = this.name
)
