# ADR 0007 · Forma del catálogo de personalización

## Estado

Aceptada. Decisión de Backend, 22 de octubre de 2026.

## Contexto

La pantalla de nueva entrevista necesita saber qué modalidades, tonos, formas de respuesta e idiomas ofrecer y cuáles están disponibles, y la creación de la sesión tiene que aceptar exactamente lo que se ofreció. Si cada lado tiene su propia lista, lo que se muestra y lo que se acepta terminan diferentes. Había que fijar la forma de la respuesta, sus nombres y de dónde sale la disponibilidad.

## Decisión

- **Una sola fuente.** `PersonalizationCatalog`, en `domain.policy`, es la única que conoce las opciones y su disponibilidad. El endpoint `GET /api/v1/interviews/config/catalog` la publica y la creación de la sesión la consulta con `isAvailable`, que rechaza con `IllegalArgumentException` cualquier enumerado que no sea uno de los cuatro.
- **Cuatro listas de `{value, available}`.** La respuesta tiene `modes`, `tones`, `answerFormats` y `languages`, en ese orden de miembros. Cada elemento lleva `value` (el valor del backlog: `SIMULACION`, `PROFESIONAL_NEUTRO`, `AUDIO`, `es-CO`) y `available` (booleano). Los nombres de las listas son los que ya usa `cameia-web`.
- **Orden.** El de declaración de cada enumerado, que es el orden en que se muestran.
- **Sin etiquetas.** El servicio no devuelve textos para mostrar («Simulación», «Próximamente»): los pone la pantalla. Así el backend no depende del idioma de la interfaz.
- **Sin preselección.** El catálogo no marca una opción por defecto; la elige la pantalla.
- **Sin caché.** La respuesta no lleva `Cache-Control`: es un objeto en memoria de unos 500 bytes y habilitar una opción debe verse en la siguiente petición.
- **Disponibilidad como constante del dominio.** Hoy solo `ENTRENO` no está disponible. Habilitarla exige cambiar ese conjunto y desplegar; no es una propiedad configurable, porque depende de que existan la repregunta y el reintento, que son código.
- **Identidad obligatoria.** Exige `X-User-Id` del Gateway; sin ella responde `401 IDENTITY_REQUIRED`. El catálogo es el mismo para todos los usuarios y planes y no lee el plan.
- **Si se agrega un valor a un enumerado,** aparece solo en la lista (la respuesta se arma recorriendo el enumerado). Una prueba compara el catálogo con las cuatro restricciones `CHECK` de la tabla de sesión, de modo que un valor que la base no admite, o que falta en el catálogo, rompe el build.

## Consecuencias

- Habilitar una opción es una línea en el dominio y un despliegue; ningún cliente cambia, porque `available` ya viaja en la respuesta.
- Agregar un campo a los elementos (por ejemplo, una etiqueta) es compatible hacia atrás para quien ignora los campos desconocidos; quitar o renombrar una lista no lo es y requiere avisar a Frontend.
- La creación de la sesión (otra tarea) no puede aceptar una opción que el catálogo marque como no disponible.
- El endpoint no toca la base de datos: responde aunque la base esté caída.

## Alternativas descartadas

- Una ruta sin identidad, como el catálogo de roles de Perfil: no da nada al usuario y abre una excepción al principio de que solo `GET /health` va sin identidad.
- Nombres de los enumerados (`modalities`, `code`) en vez de los de `cameia-web`: obligaba a Frontend a traducirlos.
- Etiquetas, preselección o `Cache-Control` en la respuesta: duplican decisiones de la pantalla o hacen invisible un cambio de disponibilidad.
- Disponibilidad configurable por propiedad: permitiría habilitar Entreno sin que exista la repregunta.
