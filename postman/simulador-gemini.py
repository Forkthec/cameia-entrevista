#!/usr/bin/env python3
"""Simulador local de Gemini (planificación) y de AssemblyAI (transcripción) para las pruebas de Postman de Entrevista.

Sirve solo para correr la colección con Newman sin gastar el cupo de los proveedores ni usar llaves reales. El servicio
se apunta a este simulador con dos variables de entorno y llaves falsas:
  CAMEIA_LLM_BASE_URL=http://localhost:8099     (Gemini: planificar las cinco preguntas)
  ASSEMBLYAI_BASE_URL=http://localhost:8099     (AssemblyAI: transcribir la respuesta por voz)
  ASSEMBLYAI_API_KEY=simulada                   (cualquier valor no vacío)
Sin dependencias: usa solo la biblioteca estándar de Python 3.

Rutas de control (las llama la colección):
  POST /__script   cuerpo {"scenario": "..."}  encola la respuesta de la siguiente llamada a un proveedor.
  POST /__reset    vacía la cola y pone el contador en cero.
  GET  /__calls    devuelve {"calls": N}: llamadas a generateContent más transcripciones pedidas (subidas de audio).

Escenarios de /__script:
  valid          plan válido de cinco preguntas (APERTURA, TECNICA, CONDUCTUAL, TECNICA, SITUACIONAL), idioma es-CO.
  bad_type       plan con el tipo de la pregunta 3 distinto al de la plantilla (la salida es inválida).
  limit          Gemini responde 429; en la transcripción, la subida responde 429.
  unavailable    el proveedor responde 503 (Gemini o la subida del audio a AssemblyAI).
  transcript     transcripción en español con confianza 0.98.
  transcript_en  transcripción en inglés con confianza 0.97 (para CA-5.12.10 en una entrevista en español).
  no_speech      transcripción vacía (AssemblyAI sin voz con on_no_speech_detected=fallback).
  error          AssemblyAI termina la transcripción con status=error.
  stuck          la transcripción se queda en status=processing: el intento agota TRANSCRIPTION_TIMEOUT (HU-5.13).

Con el reintento de HU-5.13, un 503 (unavailable) del primer intento se reintenta: para ver TRANSCRIPTION_UNAVAILABLE
hay que programar dos. Los escenarios error y stuck no se reintentan.

Gemini: cualquier ruta que termine en ":generateContent" consume el siguiente escenario; con la cola vacía, "valid".
AssemblyAI: POST /v2/upload consume el siguiente escenario (con la cola vacía, "transcript"); POST /v2/transcript y
GET /v2/transcript/{id} devuelven la transcripción de esa subida.
"""
import json
import sys
import uuid
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

TRANSCRIPTS = {
    "transcript": ("En mi último trabajo lideré un equipo de cuatro personas y resolví un conflicto de plazos.", "es",
                   0.98),
    "transcript_en": ("In my last job I led a team of four people and solved a deadline conflict.", "en", 0.97),
    "no_speech": ("", "es", None),
}
SCENARIOS = ("valid", "bad_type", "limit", "unavailable", "transcript", "transcript_en", "no_speech", "error",
             "stuck")

queue = deque()
calls = 0
uploads = {}       # upload_url -> escenario
transcripts = {}   # id -> escenario
lock = Lock()


def plan(bad_type=False):
    items = []
    for position, (kind, text) in enumerate(QUESTIONS, start=1):
        if bad_type and position == 3:
            kind = "TECNICA"
        items.append({"posicion": position, "tipo": kind, "texto": text})
    return json.dumps({"idioma": "es-CO", "preguntas": items}, ensure_ascii=False)


def gemini_body(text):
    return {
        "candidates": [{"content": {"role": "model", "parts": [{"text": text}]}, "finishReason": "STOP"}],
        "usageMetadata": {"promptTokenCount": 1200, "candidatesTokenCount": 300, "totalTokenCount": 1500},
        "modelVersion": "simulador-gemini",
    }


def transcript_body(transcript_id, scenario):
    if scenario == "stuck":
        return {"id": transcript_id, "status": "processing"}
    if scenario == "error":
        return {"id": transcript_id, "status": "error", "error": "audio ilegible (simulado)"}
    text, language, confidence = TRANSCRIPTS.get(scenario, TRANSCRIPTS["transcript"])
    return {"id": transcript_id, "status": "completed", "text": text, "language_code": language,
            "language_confidence": confidence}


class Handler(BaseHTTPRequestHandler):
    def _send(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _read(self):
        # Bytes: la subida de AssemblyAI trae el audio en binario.
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length else b""

    def do_GET(self):
        if self.path == "/__calls":
            with lock:
                self._send(200, {"calls": calls})
        elif self.path.startswith("/v2/transcript/"):
            transcript_id = self.path.rsplit("/", 1)[-1]
            with lock:
                scenario = transcripts.get(transcript_id)
            if scenario is None:
                self._send(404, {"error": "transcripción desconocida"})
            else:
                self._send(200, transcript_body(transcript_id, scenario))
        else:
            self._send(404, {"error": "ruta desconocida"})

    def do_POST(self):
        global calls
        body = self._read()
        if self.path == "/__reset":
            with lock:
                queue.clear()
                uploads.clear()
                transcripts.clear()
                calls = 0
            self._send(200, {"ok": True})
        elif self.path == "/__script":
            scenario = json.loads(body.decode("utf-8") or "{}").get("scenario")
            if scenario not in SCENARIOS:
                self._send(400, {"error": "escenario desconocido"})
                return
            with lock:
                queue.append(scenario)
            self._send(200, {"queued": scenario})
        elif self.path == "/v2/upload":
            with lock:
                calls += 1
                scenario = queue.popleft() if queue else "transcript"
            if scenario == "unavailable":
                self._send(503, {"error": "servicio no disponible (simulado)"})
            elif scenario == "limit":
                self._send(429, {"error": "límite de uso (simulado)"})
            else:
                url = "http://simulador/upload/" + uuid.uuid4().hex
                with lock:
                    uploads[url] = scenario
                self._send(200, {"upload_url": url})
        elif self.path == "/v2/transcript":
            request = json.loads(body.decode("utf-8") or "{}")
            with lock:
                scenario = uploads.pop(request.get("audio_url"), "transcript")
                transcript_id = uuid.uuid4().hex
                transcripts[transcript_id] = scenario
            self._send(200, {"id": transcript_id, "status": "queued"})
        elif self.path.endswith(":generateContent"):
            with lock:
                calls += 1
                scenario = queue.popleft() if queue else "valid"
            if scenario == "limit":
                self._send(429, {"error": {"code": 429, "message": "agotado", "status": "RESOURCE_EXHAUSTED"}})
            elif scenario == "unavailable":
                self._send(503, {"error": {"code": 503, "message": "no disponible", "status": "UNAVAILABLE"}})
            else:
                self._send(200, gemini_body(plan(bad_type=scenario == "bad_type")))
        else:
            self._send(404, {"error": "ruta desconocida"})

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
