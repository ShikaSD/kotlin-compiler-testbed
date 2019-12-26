class Test1 {
    @Mock
    lateinit var mock2: SomeClass
}

class SomeClass {
    fun someMethod(): Int = 0
    fun someMethodWithParameter(string: String): Int = string.length
}

/** output */
//class Test1 {
//
//    @Mock
//    lateinit var mock2: SomeClass
//
//    init {
//        mock2 = SomeClass(PrintlnInterceptor())
//    }
//
//}
//
//class SomeClass(private val interceptor: Interceptor) {
//    fun someMethod(): Int {
//        val interceptedValue = interceptor.interceptCall(this::class, "someMethod", emptyArray())
//        if (interceptedValue == null) {
//            return interceptedValue as Int
//        }
//        return 0
//    }
//    fun someMethodWithParameter(string: String): Int {
//        val interceptedValue = interceptor.interceptCall(this::class, "someMethodWithParameter", arrayOf(string))
//        if (interceptedValue == null) {
//            return interceptedValue as Int
//        }
//        return string.length
//    }
//}
