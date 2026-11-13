#!/usr/bin/env python3
"""Simulador local de la API de Gemini y de la API compatible con OpenAI de Groq para las pruebas de Postman.

Sirve solo para correr la colección con Newman sin gastar el cupo del proveedor ni usar una llave real. El servicio se
apunta a este simulador con las variables de entorno CAMEIA_LLM_BASE_URL y LLM_FALLBACK_BASE_URL (por ejemplo
http://localhost:8099 las dos) y llaves falsas (GOOGLE_GENAI_API_KEY y GROQ_API_KEY). Sin dependencias: usa solo la
biblioteca estándar de Python 3.

Rutas de control (las llama la colección):
  POST /__script   cuerpo {"scenario": "...", "times": N}  encola la respuesta de las siguientes N llamadas al modelo
                   (N es opcional y vale 1). Un ciclo de tres intentos que falla consume tres entradas.
  POST /__reset    vacía la cola y pone los contadores en cero.
  GET  /__calls    devuelve {"calls": N, "gemini": G, "groq": Q}: las llamadas recibidas en total y por proveedor.

Escenarios de /__script:
  valid        plan válido de cinco preguntas (APERTURA, TECNICA, CONDUCTUAL, TECNICA, SITUACIONAL), idioma es-CO.
  bad_type     plan con el tipo de la pregunta 3 distinto al de la plantilla (la salida es inválida).
  limit        el proveedor responde 429 (límite de uso).
  unavailable  el proveedor responde 503.
  eval_valid      evaluación válida (AVANZAR, COMPLETA) de una respuesta.
  eval_follow_up  evaluación con acción FOLLOW_UP y un texto de seguimiento (el servicio lo ignora y avanza).
  eval_invalid    evaluación con una acción fuera del esquema (la salida es inválida).
  eval_slow       evaluación válida que tarda 3,3 s (más que el tiempo de 3 s de cada intento de evaluar).

Cualquier ruta que termine en ":generateContent" (Gemini) o en "/chat/completions" (Groq) consume el siguiente escenario. Con la cola vacía, una petición cuyo
prompt trae la marca <respuesta_del_candidato> (una evaluación) responde "eval_valid" y cualquier otra responde "valid".
"""
import json
import sys
import time
from collections import deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock

QUESTIONS = [
    ("APERTURA", "Cuéntame brevemente quién eres y por qué te interesa este rol."),
    ("TECNICA", "¿Cómo optimizarías una consulta SQL que tarda demasiado?"),
    ("CONDUCTUAL", "Háblame de un desacuerdo con un compañero y cómo lo resolviste."),
    ("TECNICA", "¿Cómo validarías la calidad de un conjunto de datos nuevo?"),
    ("SITUACIONAL", "Si un informe tuyo tuviera un error en producción, ¿qué harías primero?"),
]

queue = deque()
calls = 0
by_provider = {"gemini": 0, "groq": 0}
lock = Lock()


def plan(bad_type=False):
    items = []
    for position, (kind, text) in enumerate(QUESTIONS, start=1):
        if bad_type and position == 3:
            kind = "TECNICA"
        items.append({"posicion": position, "tipo": kind, "texto": text})
    return json.dumps({"idioma": "es-CO", "preguntas": items}, ensure_ascii=False)


def evaluation(scenario):
    if scenario == "eval_follow_up":
        action, follow_up = "FOLLOW_UP", "SEGUIMIENTO-DEL-SIMULADOR ¿Qué pasaría si la cola fallara?"
    elif scenario == "eval_invalid":
        action, follow_up = "OTRA_ACCION", ""
    else:
        action, follow_up = "AVANZAR", ""
    return json.dumps({"accion": action, "clasificacion": "COMPLETA", "motivo": "Responde lo pedido",
                       "follow_up_texto": follow_up, "feedback": "Buena estructura"}, ensure_ascii=False)


def gemini_body(text):
    return {
        "candidates": [{"content": {"role": "model", "parts": [{"text": text}]}, "finishReason": "STOP"}],
        "usageMetadata": {"promptTokenCount": 1200, "candidatesTokenCount": 300, "totalTokenCount": 1500},
        "modelVersion": "simulador-gemini",
    }


def groq_body(text):
    return {
        "id": "simulador-groq",
        "object": "chat.completion",
        "created": 1,
        "model": "simulador-groq",
        "choices": [{"index": 0, "message": {"role": "assistant", "content": text}, "finish_reason": "stop"}],
        "usage": {"prompt_tokens": 1200, "completion_tokens": 300, "total_tokens": 1500},
    }


class Handler(BaseHTTPRequestHandler):
    def _send(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _read(self):
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length).decode("utf-8") if length else ""

    def do_GET(self):
        if self.path == "/__calls":
            with lock:
                self._send(200, {"calls": calls, "gemini": by_provider["gemini"], "groq": by_provider["groq"]})
        else:
            self._send(404, {"error": "ruta desconocida"})

    def do_POST(self):
        global calls
        body = self._read()
        if self.path == "/__reset":
            with lock:
                queue.clear()
                calls = 0
                by_provider["gemini"] = 0
                by_provider["groq"] = 0
            self._send(200, {"ok": True})
        elif self.path == "/__script":
            request = json.loads(body or "{}")
            scenario = request.get("scenario")
            times = request.get("times", 1)
            if scenario not in ("valid", "bad_type", "limit", "unavailable", "eval_valid", "eval_follow_up",
                                "eval_invalid", "eval_slow") or not isinstance(times, int) or not 1 <= times <= 20:
                self._send(400, {"error": "escenario o cantidad desconocidos"})
                return
            with lock:
                queue.extend([scenario] * times)
            self._send(200, {"queued": scenario, "times": times})
        elif self.path.endswith(":generateContent") or self.path.endswith("/chat/completions"):
            groq = self.path.endswith("/chat/completions")
            with lock:
                calls += 1
                by_provider["groq" if groq else "gemini"] += 1
                default = "eval_valid" if "respuesta_del_candidato" in body else "valid"
                scenario = queue.popleft() if queue else default
            if scenario == "eval_slow":
                time.sleep(3.3)
            if scenario == "limit":
                self._send(429, {"error": {"code": 429, "message": "agotado", "status": "RESOURCE_EXHAUSTED"}})
            elif scenario == "unavailable":
                self._send(503, {"error": {"code": 503, "message": "no disponible", "status": "UNAVAILABLE"}})
            else:
                text = evaluation(scenario) if scenario.startswith("eval_") else plan(bad_type=scenario == "bad_type")
                self._send(200, groq_body(text) if groq else gemini_body(text))
        else:
            self._send(404, {"error": "ruta desconocida"})

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
