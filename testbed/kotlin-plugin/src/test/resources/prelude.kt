annotation class Mock
interface Interceptor {
    fun interceptCall(name: String, params: Array<Any?>): Any?
}
