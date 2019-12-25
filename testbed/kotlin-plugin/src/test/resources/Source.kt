class Test {
    @Mock
    lateinit var mock1: SomeInterface
}

interface SomeInterface {
    fun someMethod(): Int
}

annotation class Mock

//// output
// class Test {
//    @Mock
//    lateinit var mock1: SomeInterface
//
//   class mock1_Mock : SomeInterface {
//        override fun someMethod(): Int {
//            return null as Int
//        }
//    }
//}
