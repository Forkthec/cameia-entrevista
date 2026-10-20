# Entrada 02 · Cliente de Gemini con Spring AI y timeout configurable

- **Fecha:** 2026-10-20 · **Herramienta:** Claude Code (Claude Opus 5.5) · **Responsable:** Juan David Vela Coronado
- **Salida:** [Spec de CM-312](../../specs/CM-312-ClienteGemini/spec.md), sección 3 (decisiones)

## Prompt

```
Objetivo general: Necesito que me ayudes a hacer la implementación de CM-211, más especificamente la CM-312 de jira.

Criterios de los que no se habla en jira: (1) una llamada de prueba devuelve contenido no vacío; (2) al superar el timeout,
el error se traduce en 503 con code LLM_UNAVAILABLE; (3) sin la llave, el servicio lo detecta al arrancar y la llave no
aparece en el log; sin llaves en el repositorio. Si el spike HU-5.14 elige Gemini para transcribir, la misma llave sirve.

Vas a necesitar implementar un RouterLLM que se comunique con el proveedor del LLM, en este caso es Gemini 3.7 Flash, esto
lo saque del diagrama de componentes que te lo pasaré, pero ten MUY EN CUENTA, que es solo un modelo propuesto y que
revisandolo hay cosas que no me cuadran del todo como el por que carajos el couta de guarda no se comunica con nadie.

El diagrama se encontrará en "/docs"

Restricciones para esta sesion:
- Comentame que componentes vas a tocar, manipular o agregar
- registra este prompt en la bitacora por mi, con mi nombre, DESPUES HAZ EL PLAN
```
