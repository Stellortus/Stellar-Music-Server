package top.stellortus.stellar_music_server.util.extensions

import io.ktor.server.routing.*
import io.ktor.util.*
import top.stellortus.stellar_music_server.auth.Argon2PasswordHasher
import java.io.File


class RouteDependencies(
    val tracksDir: File,
    val passwordHasher: Argon2PasswordHasher,
)

private val RouteDependenciesKey = AttributeKey<RouteDependencies>("stellortus.routeDependencies")

fun Route.installRouteDependencies(dependencies: RouteDependencies) {
    attributes[RouteDependenciesKey] = dependencies
}

private val Route.dependencies: RouteDependencies
    get() = generateSequence(this) { it.parent }
        .firstNotNullOf { it.attributes.getOrNull(RouteDependenciesKey) }

val Route.tracksDir: File get() = dependencies.tracksDir

val Route.passwordHasher: Argon2PasswordHasher get() = dependencies.passwordHasher
