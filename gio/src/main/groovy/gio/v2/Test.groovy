package gio.v2

class Test {

    def test = {
        // Configurando as extensões
        use(IOCategory) {

            // Exemplo 1: I/O bound com virtual threads
            def exemploIOBound = {
                def tarefasIO = (1..5).collect { i ->
                    IO.of({ ->
                        println "Tarefa I/O $i iniciada - Thread: ${Thread.currentThread().name}"
                        Thread.sleep(500) // Simula I/O
                        "IO Result $i"
                    }).virtual() // Executa em virtual thread
                }

                def resultados = tarefasIO.par().run()
                println "Resultados I/O: $resultados"
            }

            // Exemplo 2: CPU bound com carrier threads
            def exemploCpuBound = {
                def tarefasCPU = (1..3).collect { i ->
                    IO.of({ ->
                        println "Tarefa CPU $i iniciada - Thread: ${Thread.currentThread().name}"
                        // Simula processamento CPU intensivo
                        def result = 0
                        1000000.times { result += it % 100 }
                        "CPU Result $i: $result"
                    }).cpuBound() // Executa em carrier thread
                }

                def resultados = tarefasCPU.par().run()
                println "Resultados CPU: $resultados"
            }

            // Exemplo 3: Mix de tarefas
            def exemploMix = {
                def ioTask = IO.of({ ->
                    println "I/O Task - Thread: ${Thread.currentThread().name}"
                    Thread.sleep(300)
                    "I/O Complete"
                }).virtual()

                def cpuTask = IO.of({ ->
                    println "CPU Task - Thread: ${Thread.currentThread().name}"
                    def sum = 0
                    500000.times { sum += it % 50 }
                    "CPU Complete: $sum"
                }).cpuBound()

                def resultado = ioTask.flatMap { ioResult ->
                    cpuTask.map { cpuResult ->
                        "$ioResult + $cpuResult"
                    }
                }.run()

                println "Resultado misto: $resultado"
            }

            // Exemplo 4: Milhares de virtual threads
            def exemploMassivo = {
                def muitasTarefas = (1..1000).collect { i ->
                    IO.of({ ->
                        if (i % 100 == 0) {
                            println "Tarefa $i - Thread: ${Thread.currentThread().name}"
                        }
                        Thread.sleep(10)
                        "Task $i"
                    }).virtual()
                }

                def inicio = System.currentTimeMillis()
                def resultados = muitasTarefas.par().run()
                def fim = System.currentTimeMillis()

                println "1000 tarefas em ${fim - inicio}ms"
                println "Primeiros 5 resultados: ${resultados[0..4]}"
            }

            // Executando exemplos
            println "=== Estatísticas do Pool ==="
            println Runtime.poolStats

            println "\n=== Exemplo I/O Bound ==="
            exemploIOBound()

            println "\n=== Exemplo CPU Bound ==="
            exemploCpuBound()

            println "\n=== Exemplo Mix ==="
            exemploMix()

            println "\n=== Exemplo Massivo ==="
            exemploMassivo()
        }

        // Não esquecer de desligar o pool
        Runtime.shutdown()
    }

    def testWithMultipleThreadPool = {
        def ioPool = ThreadManager.getPool('io-pool', 4)
        def cpuPool = ThreadManager.getPool('cpu-pool', 8)
        println ThreadManager.stats()
    }
}
