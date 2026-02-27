package uz.zero.mmapp

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDate

@NoRepositoryBean
interface BaseRepository<T : BaseEntity> : JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    fun findByIdAndDeletedFalse(id: Long): T?
    fun trash(id: Long): T?
    fun trashList(ids: List<Long>): List<T?>
    fun findAllNotDeleted(): List<T>
}


class BaseRepositoryImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, Long>, entityManager: EntityManager
) : SimpleJpaRepository<T, Long>(entityInformation, entityManager), BaseRepository<T> {

    val isNotDeletedSpecification = Specification<T> { root, _, cb -> cb.equal(root.get<Boolean>("deleted"), false) }

    override fun findByIdAndDeletedFalse(id: Long) = findByIdOrNull(id)?.run { if (deleted) null else this }

    @Transactional
    override fun trash(id: Long): T? = findByIdOrNull(id)?.run {
        deleted = true
        save(this)
    }

    override fun findAllNotDeleted(): List<T> = findAll(isNotDeletedSpecification)
    @Transactional
    override fun trashList(ids: List<Long>): List<T?> = ids.map { trash(it) }
}

@Repository
interface UserRepository : BaseRepository<User> {
    fun findByUsername(username: String): User?
}

@Repository
interface CategoryRepository : BaseRepository<Category> {
    fun findByNameIgnoreCase(name: String): Category?
}

@Repository
interface ExpensesRepository : BaseRepository<Expenses> {
    
    fun findAllByCreatedByAndDeletedFalse(userId: Long): List<Expenses>

    @Query("SELECT e FROM Expenses e WHERE e.createdBy = :userId AND e.deleted = false AND e.date BETWEEN :startDate AND :endDate")
    fun findAllByDateBetween(startDate: LocalDate, endDate: LocalDate, userId: Long): List<Expenses>

    @Query("SELECT SUM(e.amount) FROM Expenses e WHERE e.createdBy = :userId AND e.deleted = false AND e.date BETWEEN :startDate AND :endDate")
    fun sumAmountByDateBetween(startDate: LocalDate, endDate: LocalDate, userId: Long): Double?

    @Query("SELECT e.category.name as categoryName, SUM(e.amount) as totalAmount " +
           "FROM Expenses e " +
           "WHERE e.createdBy = :userId AND e.deleted = false AND e.date BETWEEN :startDate AND :endDate " +
           "GROUP BY e.category.name")
    fun findCategoryStatistics(startDate: LocalDate, endDate: LocalDate, userId: Long): List<CategoryStatProjection>
}

interface CategoryStatProjection {
    val categoryName: String
    val totalAmount: Double
}
