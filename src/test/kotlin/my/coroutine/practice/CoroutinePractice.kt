package my.coroutine.practice

import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import net.bytebuddy.implementation.bind.annotation.Super
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CoroutinePractice {

    @Test
    @DisplayName("코루틴은 스레드에 국한되지 않는다.")
    fun test1() = runBlocking {
        for (i in 1..3) {
            launch(Dispatchers.Default) {
                println("Started $i in ${Thread.currentThread().name}")
                delay(2000)
                println("Finished $i in ${Thread.currentThread().name}")
            }
        }
    }

    @Test
    @DisplayName("코루틴은 가볍기 때문에 수만개를 만들어도된다.")
    fun test2() = runBlocking {
        println("${Thread.activeCount()} threads active at the start")
        val time = measureTimeMillis {
            val jobs = ArrayList<Job>()
            for (i in 1..50000) {
                jobs += launch(Dispatchers.Default) {
                    delay(1000)
                }
            }
            jobs.forEach { it.join() }
        }
        println("${Thread.activeCount()} threads active at the end")
        println("Took $time ms")
    }

    @Test
    @DisplayName("suspension point는 스레드를 블로킹하지 않는다.")
    fun test3() = runBlocking {
        println("runBlocking on ${Thread.currentThread().name}")
        val time = measureTimeMillis {
            val job1 = launch {
                delay(1000)
                println("job1 on ${Thread.currentThread().name}")
            }
            val job2 = launch {
                delay(1000)
                println("job2 on ${Thread.currentThread().name}")
            }
            job1.join()
            job2.join()
        }
        println("Took $time ms")
    }

    @Test
    @DisplayName("suspend function은 순차적으로 실행된다.")
    fun test4() = runBlocking {
        val time = measureTimeMillis {
            val name = async { getName() }
            val lastName = async { getLastName() }
//            println("Hello, $name $lastName")
            println("Hello, ${name.await()} ${lastName.await()}")
        }
        println("Took $time ms") // 2000ms => async 실행가능
    }

    private suspend fun getName(): String {
        delay(1000)
        return "Nick"
    }

    private suspend fun getLastName(): String {
        delay(1000)
        return "Choi"
    }

    @Test
    @DisplayName("suspend function는 스레드를 블록하지 않는다.")
    fun test5() = runBlocking {
        val time = measureTimeMillis {
            launch(Dispatchers.Default) { // 1
//                delay(1000) // 4  (얘가 없다면?)
                println("Good Morning") // 7
            }

            val name = getMyName() // 2
            println(name) // 5
        }
        println("Took $time ms") // 6
    }

    private suspend fun getMyName(): String {
//        delay(1000) // 3
        return "Nick"
    }

    @Test
    @DisplayName("코루틴 빌더는 코루틴 스코프 안에서만 만들 수 있다. (단순 중단함수 내에서는 생성 x")
    fun test6() = runBlocking {
        val time = measureTimeMillis {

            val job = launch {
                delay(1000)
                println("### first coroutine - ${Thread.currentThread().name}")
            }

            val deferred = async {
                delay(1000)
                println("### second coroutine - ${Thread.currentThread().name}")
            }

            createCoroutine() // 순서에 따라 소요시간 변경
            job.join()
            deferred.await()
        }

        println("### Took $time ms")
    }

    private suspend fun createCoroutine() = coroutineScope {
        val job = launch {
            delay(1000)
            println("### third coroutine - ${Thread.currentThread().name}")
        }
        job.join()
    }

    @Test
    @DisplayName("launch는 실행/취소 가능한 job을 리턴하는 코루틴 빌더이다.")
    fun test7() = runBlocking {
        val time = measureTimeMillis {
            val job1 = launch {
                delay(1000)
                println("Hello ")
            }
            val job2 = launch {
                delay(2000)
                println("World!!")
            }
            job1.cancel()
            job2.join()
        }
        println("## total time : ${time}ms")
    }

    @Test
    @DisplayName("async는 결과값을 기대할 수 있는 Deferred 를 리턴하는 코루틴 빌더이다.")
    fun test8() = runBlocking {
        val time = measureTimeMillis {
            val a: Deferred<Int> = async {
                delay(1000)
                5
            }
            val b: Deferred<Int> = async {
                delay(2000)
                6
            }
            println("## async add ${a.await() + b.await()}")
        }
        println("## total time : ${time}ms")
    }


    private val scope = CoroutineScope(Dispatchers.Default)

    @OptIn(InternalCoroutinesApi::class)
    @Test
    @DisplayName("launch/async의 예외 처리 - join")
    fun test9(): Unit = runBlocking {
        val task = async {
            throw UnsupportedOperationException("Can't do")
        }
        task.join()
        if (task.isCancelled) {
            val exception = task.getCancellationException()
            println("Error with message: ${exception.cause}")
        } else {
            println("Success")
        }
    }   // 예외처리를 하지않으면 task 값을 쓰려고 할 때 예외발생


    @Test
    @DisplayName("async의 예외 처리 - await")
    fun test10(): Unit = runBlocking {
        val task = async {
            throw UnsupportedOperationException("Can't do")
        }
        task.await() // 즉시 예외 전파
    }

    @Test
    @DisplayName("[Structured Concurrency] - 부모 코루틴이 종료되면 자식 코루틴도 종료된다.")
    fun test11() = runBlocking {
        val mainJob = launch {
            println("Starting the main job!")
            launch {
                while (true) {
                    delay(10)
                    println("I am child 1!")
                }
            }
            launch { // what if using other scope
                while (isActive) {
                    delay(20)
                    println("I am child 2!")
                }
            }
        }
        mainJob.invokeOnCompletion {
            println("The main job is completed/cancelled!")
        }

        delay(50) // main thread

        // this will cancel the main coroutine
        // and all its children
        mainJob.cancel()
        println("Finishing main()...")
    }

    @Test
    @DisplayName("[Structured Concurrency] - 부모 코루틴에서 예외가 발생하면 자식 코루틴도 종료된다.")
    fun test12() = runBlocking {
        val mainJob = launch {
            println("Starting the main job!")
            launch {
                while (true) {
                    delay(10)
                    println("I am child 1!")
                }
            }
            launch { // what if using other scope
                while (isActive) {
                    delay(20)
                    println("I am child 2!")
                }
            }
            throw IllegalStateException("invoke Error!!") // 부모 에러
        }
        mainJob.invokeOnCompletion {
            println("The main job is completed/cancelled!")
        }

        delay(50) // main thread
        println("Finishing main()...")
    }

    @Test
    @DisplayName("[Structured Concurrency] - 자식 코루틴이 취소되면 부모/형제 코루틴은 취소되지 않는다.")
    fun test13() = runBlocking {
        val mainJob = launch {
            println("Starting the main job!")

            val job1 = launch {
                while (true) {
                    delay(10)
                    println("I am child 1!")
                }
            }
            job1.cancel()
            launch {
                while (isActive) {
                    delay(20)
                    println("I am child 2!")
                }
            }
        }
        mainJob.invokeOnCompletion {
            println("The main job is completed/cancelled!")
        }

        delay(50) // main thread
        println("Finishing main()...")
    }

    @Test
    @DisplayName("[Structured Concurrency] - 자식 코루틴에서 예외가 전파되면 부모/형제 코루틴은 취소된다.")
    fun test14() = runBlocking {
        val mainJob = launch { // coroutineScope 과 차이
            println("Starting the main job!")

            launch {
                while (true) {
                    println("I am child 1!")
                    delay(10)
                }
            }

            launch {
                while (isActive) {
                    throw IllegalStateException("invoke error!!!")
                }
            }
        }
        mainJob.invokeOnCompletion {
            println("The main job is completed/cancelled!")
        }

        delay(50) // main thread
        println("Finishing main()...")
    }

    @Test
    @DisplayName("[Structured Concurrency] - SupervisorJob은 자식 코루틴에서만 예외를 처리한다.")
    fun test15() = runBlocking {
        val mainJob = supervisorScope {
            println("Starting the main job!")

            launch {
                while (true) {
                    println("I am child 1!")
                    delay(10)
                }
            }

            launch {
                while (isActive) {
                    throw IllegalStateException("invoke error!!!")
                }
            }
        }
        mainJob.invokeOnCompletion {
            println("The main job is completed/cancelled!")
        }

        delay(50) // main thread
        println("Finishing main()...")
    }

    @Test
    @DisplayName("예외처리 - CoroutineExceptionHandler")
    fun test16() = runBlocking {
        val mainJob = supervisorScope {
            println("Starting the main job!")

            launch {
                var i = 1
                while (i in 1..5) {
                    println("I am child 1!")
                    delay(10)
                    i++
                }
            }

            launch(ceh) {
                while (isActive) {
                    throw IllegalStateException("invoke error!!!")
                }
            }
        }
        mainJob.invokeOnCompletion {
            println("The main job is completed/cancelled!")
        }

        delay(50) // main thread
        println("Finishing main()...")
    }

    private val ceh = CoroutineExceptionHandler { coroutineContext, throwable ->
        println("Handler has caught: ${throwable.message}")
    }

    @Test
    @DisplayName("예외처리 - invokeOnCompletion")
    fun test17() = runBlocking {
        val mainJob = launch {
            println("Starting the main job!")

            launch {
                while (true) {
                    delay(10)
                    println("I am child 1!")
                }
            }

            launch {
                while (isActive) {
                    delay(20)
                    println("I am child 2")
                }
            }
        }

        mainJob.invokeOnCompletion { throwable ->
            when (throwable) {
                is CancellationException -> println("Job was cancelled!")
                is Throwable -> println("Job failed with exception!")
                null -> println("Job completed normally!")
            }
        }

        delay(50) // main thread
        mainJob.cancel()
        println("Finishing main()...")
    }
}
