package uz.zero.mmapp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Positive
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDate
import java.util.*

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
@JsonIgnoreProperties(value = ["hibernateLazyInitializer", "handler"])
open class BaseEntity(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,

    @CreatedDate @Column(updatable = false) var createdDate: Date? = null,

    @LastModifiedDate var updatedDate: Date? = null,

    @CreatedBy var createdBy: Long? = null,

    @Column(nullable = false) @ColumnDefault(value = "false") var deleted: Boolean = false,

    @Column(nullable = false) var isActive: Boolean = true

)

@Entity
@Table(name = "users")
class User(
    @Column(unique = true)
    var username: String,
    var password:  String,
    var fullName: String? = null,
    @Enumerated(EnumType.STRING)
    var role: UserRole = UserRole.ROLE_USER
): BaseEntity()



@Entity
class Expenses(

    var title: String,

    @ManyToOne(fetch = FetchType.LAZY)
    var category: Category?,
    var description: String? = null,

    @field:Positive
    var amount: Double,
    @field:PastOrPresent(message = "DATE_CANNOT_BE_FUTURE")
    var date: LocalDate
): BaseEntity()

@Entity
class Category(
    var name: String,
): BaseEntity()