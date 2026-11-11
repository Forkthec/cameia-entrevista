-- Marca de planificacion en curso: una sola ejecucion de la IA PLANIFICAR en estado EN_CURSO por sesion.
--
-- Antes de llamar al modelo, el servicio abre una fila ejecucion_ia PLANIFICAR/EN_CURSO; si otro pedido de inicio
-- de la misma sesion llega a la vez, este indice rechaza su marca y el servicio responde conflicto sin llamar a la
-- IA. Al terminar la llamada la fila pasa a EXITOSA o FALLIDA y deja de ocupar el indice.

CREATE UNIQUE INDEX uq_ejecucion_ia_planificacion_en_curso
    ON microentrevista.ejecucion_ia (sesion_id)
    WHERE tipo_operacion = 'PLANIFICAR' AND estado = 'EN_CURSO';
