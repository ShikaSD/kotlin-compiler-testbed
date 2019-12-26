class Test {
    @Mock
    lateinit var mock1: SomeInterface
}

interface SomeInterface {
    fun someMethod(): Int
    fun someMethodWithParameter(string: String): Int
}

/** output */
// class Test {
//
//    @Mock
//    lateinit var mock1: SomeInterface
//
//    init {
//        mock1 = mock1_Mock(PrintlnInterceptor())
//    }
//
//    class mock1_Mock(val interceptor: Interceptor) : SomeInterface {
//        override fun someMethod(): Int {
//            val interceptResult = interceptor.interceptCall(this::class, "someMethod", arrayOf())
//            return if (interceptResult == null) {
//                throw IllegalStateException()
//            } else {
//                interceptResult as Int
//            }
//        }
//
//        override fun someMethodWithParameter(string: String): Int {
//            val interceptResult = interceptor.interceptCall(this::class, "someMethodWithParameter", arrayOf(string))
//            return if (interceptResult == null) {
//                throw IllegalStateException()
//            } else {
//                interceptResult as Int
//            }
//        }
//
//        override fun equals(other: Any?): Boolean {
//            val interceptResult = interceptor.interceptCall(this::class, "equals", arrayOf(other))
//            return if (interceptResult == null) {
//                throw IllegalStateException()
//            } else {
//                interceptResult as Boolean
//            }
//        }
//
//        override fun hashCode(): Int {
//            // etc
//        }
//
//        override fun toString(): String {
//            // etc
//        }
//    }
//}
