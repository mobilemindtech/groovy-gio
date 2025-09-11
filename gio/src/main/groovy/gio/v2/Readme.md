Esta implementação combina:

1. **Virtual Threads** para I/O bound operations
2. **Platform Threads** para CPU bound operations
3. **Work stealing** entre threads carrier
4. **Gerenciamento automático** do tipo de thread baseado na tarefa
5. **API fluente** com extensões Groovy

A estratégia híbrida maximiza a eficiência usando virtual threads para I/O (que podem ser milhões) e platform threads para CPU (limitadas aos cores disponíveis), com work stealing para balancear a carga entre as carrier threads.