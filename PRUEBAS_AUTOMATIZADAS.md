# Pruebas automatizadas de órdenes de cobranza

Ejecutar desde la raíz del proyecto:

```bash
mvn clean test
```

La clase `GenerateCollectionOrdersUseCaseTest` comprueba:

- Cuotas vencidas, pagadas y fuera del horizonte.
- Pago parcial de S/ 500: saldo de la cuota 2 de S/ 566.19 y total S/ 2,698.57.
- Cuota 2 cancelada: se excluye y quedan dos órdenes por S/ 2,132.38.
- Inclusión de fecha límite y exclusión del día posterior.
- Múltiples préstamos en un solo lote.
- Validación de solicitudes incorrectas.

Las pruebas son unitarias con Mockito: no realizan llamadas HTTP a Fineract ni escriben en PostgreSQL.
Los importes son escenarios reproducidos mediante mocks a partir de la validación manual.
Para validar JDBC, Flyway y HTTP reales se necesitan pruebas de integración adicionales.

También se corrigió `PostgresCollectionBatchAdapter` para solicitar solo la clave generada `id`.
