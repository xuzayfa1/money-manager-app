package uz.zero.mmapp

import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class DataLoader(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        if (userRepository.findByUsername("admin") == null) {
            val admin = User(
                username = "admin",
                password = passwordEncoder.encode("admin123"),
                fullName = "System Administrator",
                role = UserRole.ROLE_ADMIN
            )
            userRepository.save(admin)
            println("Admin user created: admin/admin123")
        }
    }
}
