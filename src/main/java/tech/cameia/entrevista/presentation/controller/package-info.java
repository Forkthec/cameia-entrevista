/**
 * Adaptadores de entrada HTTP.
 *
 * <p>Identidad: el Gateway envía la persona en el encabezado {@code X-User-Id} y es la única fuente;
 * una identidad en el cuerpo, la ruta o la consulta nunca se usa. Todo controlador que la necesite
 * la declara así y la valida como primera línea del caso de uso, para que su ausencia o un valor
 * inválido responda {@code 401 IDENTITY_REQUIRED} y no un error genérico:</p>
 *
 * <pre>{@code
 * @Parameter(hidden = true)
 * @RequestHeader(value = "X-User-Id", required = false) String uid
 * ...
 * FirebaseUid owner = FirebaseUid.required(uid);
 * }</pre>
 */
package tech.cameia.entrevista.presentation.controller;
