# Arquitectura AWS — Collection Hub

## Alcance
Propuesta de despliegue, no infraestructura aprovisionada. La demostración funcional utiliza Docker Compose local. Java 21, Spring Boot 3.3, Fineract 1.12.1 y PostgreSQL 16.

## Servicios y decisiones

| Servicio | Responsabilidad y justificación |
|---|---|
| API Gateway HTTP API | Entrada HTTPS, autorización JWT, límites de solicitudes; VPC Link hacia ALB interno |
| ECS Fargate | Ejecutar Hub y Fineract como servicios independientes sin administrar servidores |
| ALB interno | Distribuir tráfico hacia Hub y comprobar salud |
| ECS Service Connect | Descubrimiento privado de Fineract; validar TLS y nombre del certificado |
| RDS PostgreSQL | Dos instancias independientes, una por sistema; Multi-AZ y respaldos según criticidad |
| ECR | Imágenes versionadas, escaneo y despliegue reproducible |
| Secrets Manager | Credenciales separadas por servicio, acceso por roles IAM |
| CloudWatch | Logs, métricas y alarmas sin registrar secretos ni datos financieros innecesarios |

## Red y seguridad
VPC en dos zonas de disponibilidad. ALB y tareas ECS en subredes privadas; RDS en subredes aisladas. Sin IP pública en tareas o bases. Security groups: VPC Link → ALB TCP 443; ALB → Hub TCP 8081 (configurar TLS interno si se requiere); Hub → Fineract TCP 8443; cada aplicación → su propia base TCP 5432. El Hub no accede a las tablas de Fineract. Usar endpoints privados para ECR, S3, Secrets Manager y CloudWatch cuando proceda; NAT solo si es necesario. TLS validado, cifrado RDS con KMS, mínimo privilegio IAM y rotación de secretos.

## Flujo
1. Consumidor autorizado solicita un lote por HTTPS.
2. Hub consulta cronogramas mediante API REST de Fineract y valida componentes monetarios.
3. Hub persiste órdenes y lote en su RDS; devuelve identificador y estado.
4. Un pago registrado en Fineract cambia el saldo fuente. Una nueva sincronización genera otro snapshot con una clave de idempotencia distinta; un replay conserva el lote anterior.

## Decisiones de diseño
- Arquitectura hexagonal: puertos separan reglas de negocio de HTTP y persistencia; facilita pruebas y sustitución de adaptadores.
- Fineract es fuente de verdad financiera; Hub mantiene una proyección local, no calcula intereses.
- Snapshots por lote preservan trazabilidad; la vigencia depende de la última sincronización.
- Idempotencia por solicitud evita repetir operaciones ante reintentos. No garantiza por sí sola atomicidad distribuida; conciliar pagos de resultado incierto antes de reintentar.
- Flyway para Hub y Liquibase para Fineract; ejecutar migraciones controladas antes de escalar. Validar jobs y concurrencia de Fineract antes de aumentar réplicas.
- Definir timeouts, alarmas de errores, restauración de backups y objetivos RPO/RTO según negocio. Dimensionar costos con carga medida.

## Relación con BIAN
Fineract concentra Loan; Hub se alinea conceptualmente con Open Item Management. La separación de servicios y bases refuerza estos límites, sin implicar conformidad BIAN certificada.

## Fuentes de referencia
- https://docs.aws.amazon.com/wellarchitected/latest/framework/welcome.html
- https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-private-integrations.html
- https://docs.aws.amazon.com/AmazonECS/latest/developerguide/what-is-fargate.html
- https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Welcome.html
- https://github.com/apache/fineract/tree/1.12.1
- [Alineamiento BIAN](ALINEAMIENTO_BIAN.md)
- [Diagrama](DIAGRAMA_ARQUITECTURA.md)
