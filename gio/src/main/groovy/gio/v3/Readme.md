Esta implementação fornece:

1. **Mônada IO básica** com `map` e `flatMap`
2. **Pool de threads** com estratégia de roubo de trabalho
3. **Operações paralelas** usando work stealing
4. **Composição funcional** de operações assíncronas
5. **Extensões** para syntax mais fluente

O work stealing garante que as threads não fiquem ociosas enquanto há trabalho disponível em outras filas, melhorando a utilização da CPU em cenários de execução paralela.