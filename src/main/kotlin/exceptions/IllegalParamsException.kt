package top.stellortus.stellar_music_server.exceptions

abstract class IllegalParamsException(message: String) : RuntimeException(message)

class EmptyParamException(argName: String) : IllegalParamsException(argName)

class InvalidParamException(argName: String, value: String) : IllegalParamsException("非法参数：$argName($value)")