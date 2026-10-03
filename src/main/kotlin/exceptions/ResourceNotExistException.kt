package top.stellortus.stellar_music_server.exceptions

class ResourceNotExistException(resource: String) : RuntimeException("当前访问的资源不存在：$resource")