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
//        mock1 = mock1_Mock()
//    }
//
//    class mock1_Mock : SomeInterface {
//        override fun someMethod(): Int {
//            return null as Int
//        }
//    }
//}
