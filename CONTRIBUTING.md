# Contribución a CAMEIA

## Flujo de trabajo

1. Trabajar desde `develop` en una rama `<tipo>/CM-NNN-<descripcion-kebab-case>`.
2. Relacionar commits y Pull Request con la tarea Jira correspondiente.
3. Abrir el Pull Request hacia `develop`; usar un PR separado para promover a `main`.
4. Solicitar revisión a una persona distinta del autor.
5. Resolver conversaciones y controles requeridos antes de integrar.

Tipos admitidos: `feat`, `fix`, `test`, `docs`, `refactor`, `perf`, `build`, `ci` y `chore`.

## Documentación y seguridad

- Actualizar `README.md` cuando cambien alcance, stack, comandos, variables, contratos, pruebas o despliegue.
- No versionar secretos, credenciales, archivos `.env`, datos personales ni evidencia sin redactar.
- No afirmar como implementada una capacidad que todavía esté pendiente.
- Justificar los controles no aplicables en la plantilla del Pull Request.