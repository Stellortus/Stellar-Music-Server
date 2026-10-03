package top.stellortus.stellar_music_server.exceptions

abstract class AuthorizationException(message: String) : RuntimeException(message)

class PermissionDeniedException: AuthorizationException("Permission denied!")

class UnauthorizedException: AuthorizationException("You are not authorized!")