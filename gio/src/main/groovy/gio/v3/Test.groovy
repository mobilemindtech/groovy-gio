package gio.v3

class Test {

    def test = {
        // Exemplo 1: Operações básicas de IO
        def exemploBasico = {
            def io1 = IO.of({ ->
                println "Executando IO 1"
                Thread.sleep(100)
                "Resultado 1"
            })

            def io2 = IO.of({ ->
                println "Executando IO 2"
                Thread.sleep(200)
                "Resultado 2"
            })

            def resultado = io1.flatMap { r1 ->
                io2.map { r2 ->
                    "$r1 + $r2"
                }
            }.run()

            println "Resultado combinado: $resultado"
        }

        // Exemplo 2: Execução paralela com work stealing
        def exemploParalelo = {
            def tarefas = (1..10).collect { i ->
                IO.of({ ->
                    println "Tarefa $i executando na thread ${Thread.currentThread().name}"
                    Thread.sleep(100 + new Random().nextInt(200))
                    "Resultado $i"
                })
            }

            def inicio = System.currentTimeMillis()
            def resultados = ParallelIO.parMap(tarefas).run()
            def fim = System.currentTimeMillis()

            println "Tempo total: ${fim - inicio}ms"
            println "Resultados: $resultados"
        }

        // Exemplo 3: Composição de operações assíncronas
        def exemploComposicao = {
            def tarefaLenta = IO.of({ ->
                println "Iniciando tarefa lenta"
                Thread.sleep(1000)
                "Dados processados"
            })

            def tarefaRapida = { dados ->
                IO.of({ ->
                    println "Processando dados: $dados"
                    "Resultado final: ${dados.toUpperCase()}"
                })
            }

            def resultado = ParallelIO.async(tarefaLenta)
                .flatMap(tarefaRapida)
                .run()

            println resultado
        }

        // Executando exemplos
        println "=== Exemplo Básico ==="
        exemploBasico()

        println "\n=== Exemplo Paralelo ==="
        exemploParalelo()

        println "\n=== Exemplo Composição ==="
        exemploComposicao()

        // Não esquecer de desligar o pool
        Runtime.shutdown()
    }

    def testWithCategory = {
        use(IOCategory) {
            def io1 = IO.of({ -> "Hello" })
            def io2 = IO.of({ -> "World" })

            def resultado = io1.then { r1 ->
                io2.map { r2 ->
                    "$r1 $r2"
                }
            }.run()

            println resultado
        }
    }

}
