import kotlin.reflect.KClass

annotation class Mock
interface Interceptor {
    fun interceptCall(cls: KClass<*>, name: String, params: Array<Any?>): Any?
}
class PrintlnInterceptor : Interceptor {
    override fun interceptCall(cls: KClass<*>, name: String, params: Array<Any?>): Any? {
        println("Call on ${cls.simpleName}, name = $name, params = ${params.joinToString(prefix = "[", postfix = "]")}")
        return null
    }

}
