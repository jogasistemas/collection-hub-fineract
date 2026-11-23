# Alineamiento BIAN

Referencia: BIAN Service Landscape 14.0 (febrero de 2026), según el informe de arquitectura previamente elaborado. Correspondencia conceptual de responsabilidades, no certificación ni implementación de contratos semánticos BIAN.

| Service Domain | Componente | Alcance y límites |
|---|---|---|
| Loan | Apache Fineract | Fuente de verdad del préstamo, condiciones, cronograma, desembolso, intereses y pagos. |
| Consumer Loan | Apache Fineract | Especialización opcional si el producto es crédito de consumo; no exige otro microservicio. |
| Open Item Management | Collection Hub | Alineación principal: obligaciones abiertas, vencimiento, capital e interés pendientes, lotes y consultas. No cubre toda la gestión de resolución de partidas. |
| Collections | Fuera del alcance | Recuperación de cuentas problemáticas y gestión de garantías; no se implementa una plataforma integral de recuperación. |

## Interacciones
El Hub obtiene los datos de Loan mediante un adaptador REST y genera su propia representación de obligaciones abiertas. Los pagos se registran en Fineract; una nueva sincronización actualiza la vista de cobranza mediante un nuevo lote. Una orden no representa una transferencia de fondos.

## Fuentes
- BIAN Service Landscape: https://bian.org/deliverables/service-landscape/
- Loan 14.0: https://bian.org/servicelandscape-14-0-0/object_16.html?object=45523
- Open Item Management 14.0: https://bian.org/servicelandscape-14-0-0/views/view_41114.html
- Apache Fineract: https://fineract.apache.org/docs/1.12.1/
