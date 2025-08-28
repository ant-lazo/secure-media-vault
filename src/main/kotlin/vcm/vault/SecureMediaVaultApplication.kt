package vcm.vault

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SecureMediaVaultApplication

fun main(args: Array<String>) {
	runApplication<SecureMediaVaultApplication>(*args)
}
