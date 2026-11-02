package com.iescristobaldemonroy.gemini_api.controller;

import com.iescristobaldemonroy.gemini_api.dto.PeticionChat;
import com.iescristobaldemonroy.gemini_api.dto.RespuestaChat;
import com.iescristobaldemonroy.gemini_api.service.GeminiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final int MAX_CARACTERES_PROMPT = 4000;
    private final GeminiService geminiService;

    public ChatController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    // Endpoint tradicional (se mantiene intacto)
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> enviarMensaje(@RequestBody PeticionChat peticion, HttpSession session) {
        try {
            if (peticion == null || peticion.getMensaje() == null || peticion.getMensaje().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new RespuestaChat("El mensaje no puede estar vacío."));
            }

            String mensajeLimpio = peticion.getMensaje().trim();

            if (mensajeLimpio.length() > MAX_CARACTERES_PROMPT) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(new RespuestaChat("El mensaje excede el límite permitido de " + MAX_CARACTERES_PROMPT + " caracteres."));
            }

            String respuesta = geminiService.enviarMensaje(session.getId(), mensajeLimpio);
            return ResponseEntity.ok(new RespuestaChat(respuesta));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RespuestaChat("Error interno del servidor: " + e.getMessage()));
        }
    }

    // Nuevo endpoint para streaming en tiempo real
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter enviarMensajeStream(@RequestBody PeticionChat peticion, HttpSession session) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(180000L); // 3 minutos de timeout

        if (peticion == null || peticion.getMensaje() == null || peticion.getMensaje().trim().isEmpty()) {
            try {
                emitter.send("Error: El mensaje no puede estar vacío.");
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        String mensajeLimpio = peticion.getMensaje().trim();
        String sessionId = session.getId();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                geminiService.enviarMensajeStream(sessionId, mensajeLimpio, chunk -> {
                    try {
                        emitter.send(chunk);
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send("\n\n[Error al procesar streaming: " + e.getMessage() + "]");
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping("/limpiar")
    public ResponseEntity<Void> limpiarHistorial(HttpSession session) {
        geminiService.limpiarHistorial(session.getId());
        return ResponseEntity.ok().build();
    }
}