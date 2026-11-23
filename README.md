# Collection Hub — Integración con Apache Fineract

**Reto técnico Prestamype**

Solución backend desarrollada con Java 21, Spring Boot 3.3, PostgreSQL 16 y Apache Fineract 1.12.1, siguiendo los principios de arquitectura hexagonal.

El sistema permite consultar cronogramas de préstamos, sincronizar cuotas, registrar pagos mediante Fineract y generar lotes de órdenes de cobranza a partir de cuotas pendientes.

---

## 1. Tecnologías utilizadas

| Tecnología | Uso |
|---|---|
| Java 21 | Lenguaje principal |
| Spring Boot 3.3 | Framework backend |
| Maven | Compilación y pruebas |
| PostgreSQL 16 | Persistencia |
| Apache Fineract 1.12.1 | Plataforma de préstamos |
| Flyway | Migraciones de base de datos |
| Docker | Contenedorización |
| Docker Compose | Orquestación local |
| JUnit y Mockito | Pruebas automatizadas |
| REST/JSON | Comunicación entre servicios |

## 2. Arquitectura de la solución

La aplicación sigue una arquitectura hexagonal que separa las reglas de negocio de los mecanismos de infraestructura.

### Componentes principales

**Dominio**

Contiene los modelos y las reglas de negocio, sin depender directamente de Spring Boot, PostgreSQL o Apache Fineract.

**Aplicación**

Implementa los casos de uso, incluyendo la consulta de cuotas, sincronización, registro de pagos y generación de órdenes de cobranza.

**Puertos**

Definen los contratos mediante los cuales la aplicación interactúa con servicios externos y mecanismos de persistencia.

**Adaptadores**

Implementan las integraciones necesarias:

- API REST para exponer las operaciones del Collection Hub.
- Cliente HTTP para consultar y operar sobre Apache Fineract.
- Adaptadores de persistencia para PostgreSQL.

### Arquitectura de despliegue

```text
                  Cliente REST
                       |
                       v
              +------------------+
              |  Collection Hub  |
              | Java 21 / Spring |
              |    Boot 3.3      |
              +------------------+
                 |           |
                 |           |
                 v           v
        +--------------+  +------------------+
        | PostgreSQL   |  | Apache Fineract  |
        | Collection   |  |      1.12.1      |
        | Hub          |  +------------------+
        +--------------+           |
                                   v
                          +------------------+
                          | PostgreSQL       |
                          | Fineract         |
                          +------------------+
```

Collection Hub y Apache Fineract utilizan bases de datos independientes.

Collection Hub no modifica directamente las tablas internas de Apache Fineract. La integración se realiza mediante su API REST.

## 3. Requisitos

Para ejecutar la solución completa:

- Docker Desktop.
- Docker Compose.
- Git.
- Bash.
- `jq`.

Para ejecutar el backend fuera de Docker también se requiere:

- JDK 21.
- Maven 3.9 o superior.

Los comandos de esta documentación están orientados a macOS y a una terminal compatible con Bash.

## 4. Estructura del proyecto

```text
collection-hub-fineract/
|
|-- src/
|   |-- main/
|   |   |-- java/com/collectionhub/
|   |   |-- resources/
|   |       |-- db/migration/
|   |
|   |-- test/
|
|-- docker/
|   |-- fineract/
|       |-- initdb/
|           |-- 01-init.sh
|
|-- scripts/
|   |-- setup-demo.sh
|   |-- run-demo.sh
|   |-- wait-for-fineract.sh
|
|-- Dockerfile
|-- compose.yaml
|-- pom.xml
|-- .env.example
|-- .dockerignore
|-- .gitignore
|-- README.md
|-- PRUEBAS_AUTOMATIZADAS.md
```

Las carpetas de código fuente contienen los modelos, casos de uso, puertos y adaptadores de la aplicación.

## 5. Configuración del entorno

### 5.1. Preparar variables de entorno

Desde la raíz del proyecto:

```bash
cp .env.example .env
```

Editar el archivo `.env` y reemplazar los valores de ejemplo por las credenciales locales correspondientes.

Entre las variables utilizadas se encuentran:

```dotenv
HUB_DB_PASSWORD=...
FINERACT_DB_PASSWORD=...
FINERACT_POSTGRES_PASSWORD=...
FINERACT_PASSWORD=...
```

El resto de las variables debe conservar los nombres y valores adecuados definidos en `.env.example`.

**Seguridad:** el archivo `.env` contiene información sensible y no debe publicarse en GitHub.

### 5.2. Aislamiento de servicios

El despliegue utiliza el nombre de proyecto Docker Compose:

```text
prestamype-entrega
```

Esto permite diferenciar sus servicios y recursos de otros proyectos Docker.

## 6. Levantar el sistema con Docker

### 6.1. Construir e iniciar los servicios

```bash
docker compose -p prestamype-entrega up -d --build
```

El comando prepara los cuatro servicios:

| Servicio | Descripción |
|---|---|
| `hub-db` | Base de datos PostgreSQL de Collection Hub |
| `fineract-db` | Base de datos PostgreSQL de Apache Fineract |
| `fineract` | Plataforma de gestión de préstamos |
| `hub` | API REST de Collection Hub |

### 6.2. Verificar el estado

```bash
docker compose -p prestamype-entrega ps
```

### 6.3. Consultar registros

```bash
docker compose -p prestamype-entrega logs -f hub
```

Para consultar los registros de Fineract:

```bash
docker compose -p prestamype-entrega logs -f fineract
```

### 6.4. Detener los servicios

```bash
docker compose -p prestamype-entrega down
```

Este comando detiene y elimina los contenedores y la red del proyecto, pero no elimina sus volúmenes persistentes por defecto.

**Advertencia:** no ejecutar `down -v` sobre un entorno con información que se desee conservar, ya que puede eliminar los volúmenes de las bases de datos.

## 7. Preparación automática de datos de demostración

El proyecto incluye un script para preparar los recursos necesarios en Apache Fineract.

Ejecutar:

```bash
./scripts/setup-demo.sh
```

El script realiza las siguientes operaciones:

1. Espera hasta que Apache Fineract esté disponible.
2. Consulta los clientes existentes.
3. Reutiliza o crea el cliente de demostración.
4. Consulta los productos financieros.
5. Reutiliza o crea el producto financiero.
6. Busca el préstamo de demostración.
7. Reutiliza o crea una solicitud de préstamo.
8. Aprueba el préstamo cuando corresponde.
9. Realiza el desembolso cuando corresponde.
10. Guarda el identificador del préstamo para su uso posterior.

El identificador se almacena localmente en:

```text
.demo/loan-id
```

Este archivo está excluido del repositorio mediante `.gitignore`.

**Estado de validación:** la reutilización del cliente, producto y préstamo existentes ha sido comprobada. La creación completa desde una base de datos vacía todavía debe validarse de extremo a extremo.

## 8. Datos del préstamo de demostración

La demostración utiliza un préstamo con las siguientes características:

| Campo | Valor |
|---|---|
| Cliente | Ana Demo Integracion |
| Producto | Prestamype Demo PEN |
| Moneda | PEN |
| Principal | S/ 12,000.00 |
| Número de cuotas | 12 |
| Frecuencia | Mensual |
| Interés periódico | 1 % mensual |
| Fecha de desembolso | 15/09/2026 |
| Primera cuota | 15/10/2026 |

Los identificadores del cliente, producto y préstamo son asignados por Fineract. No deben asumirse como constantes en una instalación nueva.

### Ejemplo de cronograma

| Cuota | Vencimiento | Principal | Interés | Total |
|---|---|---:|---:|---:|
| 1 | 15/10/2026 | S/ 946.19 | S/ 120.00 | S/ 1,066.19 |
| 2 | 15/11/2026 | S/ 955.65 | S/ 110.54 | S/ 1,066.19 |

Los importes corresponden al préstamo de demostración validado y pueden cambiar si se modifican sus condiciones o se registran pagos.

## 9. Consultar y sincronizar cuotas

Collection Hub permite obtener los cronogramas desde Fineract y almacenar una representación local en PostgreSQL.

### Consultar cuotas directamente desde Fineract

```http
GET /api/v1/loans/{loanId}/installments
```

### Sincronizar cuotas en PostgreSQL

```http
POST /api/v1/loans/{loanId}/installments/sync
```

Esta operación consulta el cronograma y actualiza las cuotas locales.

La sincronización utiliza una estrategia de actualización para evitar insertar repetidamente la misma cuota dentro de la tabla de cuotas sincronizadas.

### Consultar cuotas almacenadas

```http
GET /api/v1/loans/{loanId}/installments/stored
```

La respuesta incluye información como:

- Número de cuota.
- Fecha de vencimiento.
- Capital e intereses.
- Importe pagado.
- Saldo pendiente.
- Estado de pago.

Las migraciones de Flyway preparan las estructuras necesarias en PostgreSQL al iniciar la aplicación.

## 10. Registro de pagos

El sistema contempla la integración con Apache Fineract para registrar pagos de préstamos.

### Endpoint

```http
POST /api/v1/loans/{loanId}/payments
```

### Flujo de procesamiento

1. Collection Hub recibe la solicitud de pago.
2. Se comprueba la clave de idempotencia.
3. Se registra el pago mediante la API de Fineract.
4. Se conserva el resultado de la operación.
5. Se intenta sincronizar nuevamente el cronograma local.

### Comportamiento ante errores de sincronización

Si Fineract confirma el pago, pero falla la actualización del cronograma local:

- El pago confirmado conserva su estado `SUCCEEDED`.
- Se registra la incidencia para conciliación.
- Puede ejecutarse posteriormente una sincronización manual.
- No debe repetirse automáticamente el pago confirmado.

Esto evita duplicar pagos como consecuencia de un fallo posterior en la persistencia local.

## 11. Generación de lotes de cobranza

Collection Hub permite generar lotes a partir de los cronogramas de préstamos administrados en Fineract.

### Endpoint

```http
POST /api/v1/collection-batches
```

### Ejemplo de solicitud

```json
{
  "loanIds": [1],
  "cutoffDate": "2026-10-15",
  "horizonDays": 31
}
```

El valor `loanIds` debe corresponder a préstamos existentes en Fineract.

### Reglas de generación

El sistema:

1. Consulta los cronogramas de los préstamos solicitados.
2. Identifica las cuotas con saldo pendiente.
3. Considera las cuotas vencidas y las que vencen dentro del horizonte definido.
4. Genera órdenes de cobranza.
5. Persiste un lote y sus órdenes en PostgreSQL.
6. Devuelve el resultado del procesamiento.

La fecha final del horizonte se calcula a partir de la fecha de corte y el número de días solicitado.

### Ejemplo de respuesta

```json
{
  "batchId": 4,
  "status": "COMPLETED",
  "loansProcessed": 1,
  "ordersGenerated": 2,
  "cutoffDate": "2026-10-15",
  "horizonEnd": "2026-11-15"
}
```

Este ejemplo corresponde a una ejecución validada. El identificador del lote puede variar en otros entornos.

## 12. Órdenes de cobranza

### Consultar órdenes

```http
GET /api/v1/collection-orders?loanId=1
```

También pueden utilizarse los filtros soportados por el endpoint, como:

- `batchId`
- `loanId`
- `dueBefore`
- `status`

### Consultar un lote específico

```http
GET /api/v1/collection-batches/{batchId}
```

### Ejemplo de órdenes generadas

```json
[
  {
    "id": 7,
    "batchId": 4,
    "loanId": 1,
    "clientId": 1,
    "clientName": "Ana Demo Integracion",
    "installmentNumber": 1,
    "dueDate": "2026-10-15",
    "currency": "PEN",
    "principalOutstanding": 946.19,
    "interestOutstanding": 120.00,
    "totalOutstanding": 1066.19,
    "status": "PENDING"
  },
  {
    "id": 8,
    "batchId": 4,
    "loanId": 1,
    "clientId": 1,
    "clientName": "Ana Demo Integracion",
    "installmentNumber": 2,
    "dueDate": "2026-11-15",
    "currency": "PEN",
    "principalOutstanding": 955.65,
    "interestOutstanding": 110.54,
    "totalOutstanding": 1066.19,
    "status": "PENDING"
  }
]
```

Los importes representan el saldo pendiente de las cuotas al momento de generar el lote.

## 13. Idempotencia

La generación de lotes utiliza una clave de idempotencia proporcionada mediante la cabecera HTTP:

```http
Idempotency-Key: prestamype-loan-1-2026-10-15-31
```

### Comportamiento esperado

Cuando se repite una solicitud con la misma clave y los mismos parámetros, el sistema reutiliza el resultado registrado previamente.

Esto evita generar otro lote como consecuencia de reintentos de la misma operación.

**Importante:** una solicitud con una clave diferente puede generar un nuevo lote que contenga cuotas ya incluidas en otro lote.

Esto es coherente con el almacenamiento de snapshots históricos: la idempotencia se aplica a la solicitud, no como una prohibición global de que una cuota aparezca en lotes diferentes.

## 14. Demostración automatizada

El proyecto incorpora el script:

```text
scripts/run-demo.sh
```

### Ejecutar

```bash
./scripts/run-demo.sh
```

El script:

1. Obtiene el identificador del préstamo desde `.demo/loan-id`.
2. Espera la disponibilidad de Apache Fineract.
3. Construye la solicitud del lote de cobranza.
4. Envía la petición a Collection Hub.
5. Consulta las órdenes generadas.
6. Presenta los resultados en la terminal.

### Parámetros configurables

El script admite variables de entorno para modificar los parámetros de demostración.

Por ejemplo:

```bash
DEMO_CUTOFF_DATE=2026-10-15 \
DEMO_HORIZON_DAYS=31 \
./scripts/run-demo.sh
```

También es posible indicar un préstamo específico:

```bash
DEMO_LOAN_ID=1 ./scripts/run-demo.sh
```

La ejecución normal no requiere escribir manualmente el identificador cuando previamente se ha ejecutado `setup-demo.sh`.

### Verificar idempotencia

Ejecutar dos veces:

```bash
./scripts/run-demo.sh
./scripts/run-demo.sh
```

Con los mismos parámetros y la misma clave de idempotencia, debe recuperarse el mismo lote.

## 15. Consultas desde la red Docker

En la configuración de demostración, los servicios se comunican mediante la red interna de Docker.

### Consultar órdenes desde Collection Hub

```bash
docker run --rm \
  --network prestamype-entrega_default \
  curlimages/curl:latest \
  -sS \
  "http://hub:8081/api/v1/collection-orders?loanId=1"
```

### Consultar Fineract

```bash
docker run --rm \
  --network prestamype-entrega_default \
  curlimages/curl:latest \
  -ksS \
  -u mifos:password \
  -H "Fineract-Platform-TenantId: default" \
  "https://fineract:8443/fineract-provider/api/v1/clients"
```

Las credenciales de este último comando son únicamente las del entorno local de demostración. Deben ajustarse si se modifica la configuración de Fineract.

La opción `-k` desactiva la validación del certificado TLS y solo debe utilizarse en un entorno local controlado.

## 16. Persistencia y migraciones

Collection Hub utiliza PostgreSQL para almacenar información propia de la aplicación.

Entre los datos persistidos se encuentran:

- Cuotas sincronizadas.
- Resultados de pagos.
- Información de idempotencia.
- Lotes de cobranza.
- Órdenes de cobranza.

Flyway administra las migraciones de la base de datos.

Las tablas internas de Apache Fineract permanecen bajo la responsabilidad de Fineract.

### Verificar PostgreSQL de Collection Hub

```bash
docker compose -p prestamype-entrega exec hub-db \
  psql -U hub_user -d collection_hub
```

Dentro de PostgreSQL:

```sql
\dt
```

Para salir:

```sql
\q
```

## 17. Pruebas automatizadas

El proyecto incluye pruebas automatizadas para validar la lógica de negocio y el comportamiento de sus componentes.

Para ejecutar las pruebas mediante Maven:

```bash
mvn test
```

Para compilar y ejecutar las pruebas:

```bash
mvn clean verify
```

En una ejecución previa del proyecto se verificaron satisfactoriamente 34 pruebas.

Este resultado corresponde a la versión evaluada en ese momento y debe confirmarse nuevamente antes de la entrega final.

La documentación adicional se encuentra en:

```text
PRUEBAS_AUTOMATIZADAS.md
```

## 18. Manejo de errores y consistencia

La solución considera escenarios como:

- Fallos de comunicación con Apache Fineract.
- Solicitudes duplicadas.
- Errores al sincronizar cronogramas.
- Préstamos inexistentes.
- Cuotas sin saldo pendiente.
- Fallos de persistencia.
- Inconsistencias entre componentes del cronograma.

La generación de órdenes debe evitar registrar importes incorrectos cuando la información del cronograma no pueda interpretarse de forma consistente.

La sincronización local y el registro de pagos se tratan como operaciones diferenciadas para reducir el riesgo de repetir transacciones financieras.

## 19. Seguridad

Las principales consideraciones son:

- Las credenciales se gestionan mediante variables de entorno.
- `.env` no se incorpora al repositorio.
- Los servicios se comunican mediante la red interna de Docker.
- Los certificados autofirmados y la desactivación de validaciones TLS se limitan al entorno local.
- En producción deben configurarse certificados válidos, almacenamiento seguro de secretos y controles de acceso.
- Los datos de demostración no deben utilizarse como credenciales o información financiera de producción.

## 20. Consideraciones para producción

La implementación corresponde a un reto técnico y a un entorno de demostración.

Para un despliegue productivo sería necesario evaluar adicionalmente:

- Gestión centralizada de secretos.
- Autenticación y autorización de los consumidores de la API.
- Observabilidad, métricas y trazabilidad distribuida.
- Políticas de reintentos y resiliencia.
- Conciliación automática de pagos.
- Procesamiento asíncrono para grandes volúmenes de préstamos.
- Monitoreo de fallos y alertas.
- Políticas de respaldo y recuperación.
- Seguridad de red y certificados TLS.
- Pruebas de carga, concurrencia y recuperación.

## 21. Validaciones pendientes antes de la entrega

Aunque la demostración con los datos existentes se ha ejecutado satisfactoriamente, todavía deben completarse estas verificaciones:

- [ ] Probar la instalación completa desde bases de datos vacías.
- [ ] Confirmar la creación automática de cliente, producto y préstamo.
- [ ] Mejorar la identificación inequívoca del préstamo de demostración.
- [ ] Ejecutar nuevamente todas las pruebas automatizadas.
- [ ] Validar el comportamiento de los scripts ante fallos y reintentos.
- [ ] Revisar las credenciales fijas que todavía puedan existir en los archivos Compose.
- [ ] Retirar o actualizar la configuración antigua `docker-compose.hub.yml`.
- [ ] Revisar la documentación y los archivos antes de publicarlos en GitHub.

## 22. Ejecución rápida

Una vez configurado el archivo `.env`, el flujo principal de demostración es:

```bash
# 1. Levantar los servicios
docker compose -p prestamype-entrega up -d --build

# 2. Preparar los datos de demostracion
./scripts/setup-demo.sh

# 3. Generar y consultar las ordenes de cobranza
./scripts/run-demo.sh
```

El resultado esperado es un lote de cobranza completado y sus correspondientes órdenes, generadas a partir del cronograma de Apache Fineract.

---

## Conclusión

Collection Hub demuestra la integración de una aplicación Java basada en arquitectura hexagonal con Apache Fineract.

La solución separa las responsabilidades del dominio financiero, la comunicación con Fineract y la persistencia local.

Las funcionalidades implementadas permiten consultar y sincronizar cronogramas, procesar pagos y generar lotes de cobranza con idempotencia por solicitud.

El proyecto está preparado para continuar con las validaciones de reproducibilidad, robustez y documentación necesarias antes de su entrega final.