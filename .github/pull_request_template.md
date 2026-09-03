<!--

Plantilla CAMEIA v2.0 — 2 de septiembre de 2026

Estado: propuesta refinada, pendiente de revisión HITL, instalación y prueba.

Propósito: una sola plantilla adaptable para cambios normales, DevOps y promociones.



Título requerido:

CM-NNN | tipo(scope): resultado



Si hubo código asistido por IA:

CM-NNN | tipo(scope): resultado \[IA-ASISTIDO]



Tipos: feat, fix, test, docs, refactor, perf, build, ci, chore.

Complete las secciones obligatorias. En las secciones condicionales marque

"No aplica" y dé una razón corta cuando corresponda. No invente resultados.

\-->



\## Resumen y trazabilidad — obligatorio



\- \*\*Jira:\*\* \[CM-NNN](PEGAR\_URL)

\- \*\*Sprint:\*\*

\- \*\*Rol ejercido por el autor:\*\*

\- \*\*Tipo:\*\* <!-- elija uno --> `feat` / `fix` / `test` / `docs` / `refactor` / `perf` / `build` / `ci` / `chore`

\- \*\*Rama origen → destino:\*\* `tipo/CM-NNN-descripcion` → `develop` / `main` si es promoción

\- \*\*HU/criterio relacionado:\*\*

\- \*\*Componentes afectados:\*\*



\### ¿Qué cambia?



<!-- Explíquelo en 1–3 puntos. -->



\-



\### ¿Qué queda fuera?



<!-- Indique límites, deuda o trabajo posterior. -->



\-



\## Criterios y evidencia — obligatorio



| Criterio de aceptación o resultado esperado | Cómo se verificó | Evidencia | Resultado |

|---|---|---|---|

| | | | Pasa / Falla / Bloqueado |



\## Validaciones ejecutadas — obligatorio



<!-- Marque lo ejecutado. Si algo necesario no existe, explique el bloqueo. -->



\- \[ ] Build

\- \[ ] Pruebas unitarias

\- \[ ] Pruebas de integración

\- \[ ] Pruebas funcionales o smoke

\- \[ ] Formato/lint

\- \[ ] Análisis estático de calidad

\- \[ ] Análisis de seguridad/dependencias

\- \[ ] Detección de secretos

\- \[ ] Validación de configuración o migraciones

\- \[ ] No aplica ejecutar controles técnicos porque:



\- \*\*Workflow o evidencia:\*\*

\- \*\*Cobertura obtenida:\*\* `N/A` <!-- indique porcentaje y enlace si existe -->

\- \*\*Code smells detectados/relevantes:\*\* `N/A` / cantidad y tratamiento

\- \*\*Hallazgos relevantes:\*\*

\- \*\*Vulnerabilidades críticas abiertas:\*\* `0` / `N/A` / explicar



\## Impacto y riesgo — obligatorio



\- \*\*Nivel de riesgo:\*\* Bajo / Medio / Alto

\- \*\*Riesgo principal:\*\*

\- \*\*Compatibilidad o breaking change:\*\* No / Sí — explicar

\- \*\*Contratos, datos o migraciones:\*\* No aplica / explicar

\- \*\*Señal para verificar después del merge:\*\*

\- \*\*Rollback:\*\* Revertir PR / redesplegar digest anterior / procedimiento enlazado



\## Seguridad y privacidad — obligatorio



\- \[ ] No contiene secretos, credenciales, `.env` ni llaves privadas.

\- \[ ] No expone PII ni datos reales innecesarios en código, pruebas, logs o capturas.

\- \[ ] Se revisaron entradas, autenticación y autorización cuando aplican.

\- \[ ] Los permisos nuevos cumplen mínimo privilegio, si aplica.

\- \[ ] Los hallazgos críticos están cerrados o tienen una excepción humana enlazada.



\- \*\*Revisión OWASP aplicable o razón de no aplicación:\*\*



\## Uso de IA y revisión humana — obligatorio



<!-- Marque una sola opción. -->



\- \[ ] No se utilizó IA para este cambio.

\- \[ ] Sí se utilizó IA y el título contiene `\[IA-ASISTIDO]`.



Si se utilizó IA:



\- \*\*Herramienta:\*\*

\- \*\*Parte asistida:\*\*

\- \*\*Registro de prompt/bitácora:\*\*

\- \*\*Qué se aceptó:\*\*

\- \*\*Qué se corrigió o rechazó y por qué:\*\*

\- \*\*Validación aplicada:\*\* pruebas / revisión manual / seguridad / otra

\- \*\*Responsable humano que comprende y responde por el resultado:\*\*



\## Despliegue o promoción — completar solo si aplica



\- \*\*Ambiente:\*\* No aplica / local / staging / producción

\- \*\*Commit SHA:\*\*

\- \*\*Artefacto, imagen o digest:\*\*

\- \*\*URL o servicio:\*\*

\- \*\*Smoke test y resultado:\*\*

\- \*\*Versión anterior recuperable:\*\*

\- \*\*Aprobación de Tester/PO requerida:\*\* No / Sí — enlace



\## Datos para seguimiento TMA/GQM — breve



<!-- GitHub conserva fechas y revisiones. Registre solamente información que no pueda inferirse automáticamente. -->



\- \*\*¿Hubo bloqueo o espera relevante?:\*\* No / Sí — duración y causa

\- \*\*¿El PR volvió a Draft o requirió retrabajo por DoD/revisión?:\*\* No / Sí — motivo

\- \*\*Contribución conjunta/pairing:\*\* No / Sí — participantes

\- \*\*Decisión o aprendizaje reutilizable:\*\* No / Sí — enlace a ADR, retrospectiva o registro



\## Lista para revisión



\### Autor



\- \[ ] El cambio corresponde al alcance de Jira y puedo explicarlo.

\- \[ ] Si cambié propósito, stack, comandos, variables, contratos, arquitectura, despliegue o responsables, actualicé `README.md`; si no aplica, lo justifiqué.

\- \[ ] Actualicé contratos, documentación adicional y evidencia cuando aplica.

\- \[ ] No me asigné como única persona revisora o aprobadora.

\- \[ ] Todos los fallos, exclusiones y `N/A` están justificados.



\### Revisor distinto del autor



\- \[ ] Comprendí el alcance, el riesgo y la estrategia de prueba.

\- \[ ] Revisé los cambios y la evidencia, no solamente los checks.

\- \[ ] No quedan conversaciones bloqueantes sin resolver.

\- \[ ] Los checks obligatorios están verdes o existe excepción aprobada y vigente.

\- \[ ] El cambio puede integrarse sin declarar como terminado trabajo pendiente.